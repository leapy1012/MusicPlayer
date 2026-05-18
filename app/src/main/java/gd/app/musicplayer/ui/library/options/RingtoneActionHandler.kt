package gd.app.musicplayer.ui.library.options

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.widget.AdapterView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.dialog.MessageDialog
import gd.app.musicplayer.core.designsystem.dialog.OptionsListDialog
import gd.app.musicplayer.domain.model.Music
import kotlin.concurrent.thread

object RingtoneActionHandler {

    private var pendingRequest: PendingRingtoneRequest? = null

    fun handle(
        activity: Activity,
        music: Music,
        dialogConfigFactory: MaterialDialogConfigFactory
    ) {
        val slots = detectRingtoneSlots(activity)
        if (slots.size <= 1) {
            confirmSetRingtone(activity, music, slots.firstOrNull(), dialogConfigFactory)
            return
        }

        lateinit var dialog: OptionsListDialog
        val config = dialogConfigFactory
            .createMaterialListDialogConfig(activity, slots.map { it.label })
            .apply {
                titleText = activity.getString(R.string.dlg_ringtone_2)
                onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    dialog.dismiss()
                    confirmSetRingtone(activity, music, slots[position], dialogConfigFactory)
                }
            }

        dialog = OptionsListDialog(activity, config)
        dialog.show()
    }

    fun handlePendingPermissionResult(context: Context) {
        val request = pendingRequest ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            return
        }

        pendingRequest = null
        setRingtoneAsync(context.applicationContext, request.music, request.slot)
    }

    private fun confirmSetRingtone(
        activity: Activity,
        music: Music,
        slot: RingtoneSlot?,
        dialogConfigFactory: MaterialDialogConfigFactory
    ) {
        val titleSegment = " \"${music.title}\" "
        val message = activity.getString(R.string.ringtone_tip, titleSegment)
        val config = dialogConfigFactory
            .createMaterialMessageDialogConfig(activity)
            .apply {
                titleText = activity.getString(R.string.dlg_ringtone)
                messageText = dialogConfigFactory.createAccentMessage(message, titleSegment)
                positiveButtonText = activity.getString(R.string.confirm)
                negativeButtonText = activity.getString(R.string.cancel)
                positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                    dialog.dismiss()
                    setRingtoneWithPermission(activity, music, slot)
                }
            }

        MessageDialog.show(activity, config)
    }

    private fun setRingtoneWithPermission(
        activity: Activity,
        music: Music,
        slot: RingtoneSlot?
    ) {
        if (music.id <= 0L) {
            ToastUtil.show(activity, R.string.music_unsupported)
            return
        }

        if (!ensureWriteSettingsPermission(activity, music, slot)) return
        setRingtoneAsync(activity.applicationContext, music, slot)
    }

    private fun ensureWriteSettingsPermission(
        activity: Activity,
        music: Music,
        slot: RingtoneSlot?
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(activity)) {
            return true
        }

        pendingRequest = PendingRingtoneRequest(music, slot)
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${activity.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }
            .onFailure {
                pendingRequest = null
                ToastUtil.show(activity, R.string.dlg_ringtone_failed)
            }
        return false
    }

    private fun setRingtoneAsync(
        context: Context,
        music: Music,
        slot: RingtoneSlot?
    ) {
        thread(name = "SetRingtone") {
            val uri = resolveAudioUri(music)
            val success = runCatching {
                markAsRingtone(context, music.id)
                RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    RingtoneManager.TYPE_RINGTONE,
                    uri
                )
                writeSimRingtoneSettings(context, uri, slot)
                true
            }.getOrDefault(false)

            Handler(Looper.getMainLooper()).post {
                ToastUtil.show(
                    context,
                    if (success) R.string.dlg_ringtone_success else R.string.dlg_ringtone_failed
                )
            }
        }
    }

    private fun resolveAudioUri(music: Music): Uri {
        return ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            music.id
        )
    }

    private fun markAsRingtone(context: Context, mediaStoreId: Long) {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            mediaStoreId
        )
        context.contentResolver.update(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
                put(MediaStore.Audio.Media.IS_MUSIC, true)
            },
            "${MediaStore.Audio.Media._ID}=?",
            arrayOf(mediaStoreId.toString())
        )
    }

    private fun writeSimRingtoneSettings(
        context: Context,
        uri: Uri,
        slot: RingtoneSlot?
    ) {
        val resolver = context.contentResolver
        val uriString = uri.toString()
        val slotIndex = slot?.slotIndex ?: 0

        Settings.System.putString(resolver, Settings.System.RINGTONE, uriString)

        val keys = if (slotIndex <= 0) {
            SIM1_RINGTONE_KEYS
        } else {
            SIM2_RINGTONE_KEYS
        }

        keys.forEach { key ->
            runCatching { Settings.System.putString(resolver, key, uriString) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun detectRingtoneSlots(context: Context): List<RingtoneSlot> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return listOf(RingtoneSlot(0, context.getString(R.string.sim_1)))
        }

        val subscriptions = runCatching {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                .orEmpty()
        }.getOrDefault(emptyList())

        val activeSlots = subscriptions
            .filter { it.simSlotIndex >= 0 }
            .distinctBy { it.simSlotIndex }
            .sortedBy { it.simSlotIndex }

        if (activeSlots.size < 2) {
            val phoneCount = runCatching {
                val telephonyManager = context.getSystemService(TelephonyManager::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    telephonyManager?.activeModemCount ?: 1
                } else {
                    @Suppress("DEPRECATION")
                    telephonyManager?.phoneCount ?: 1
                }
            }.getOrDefault(1)

            return if (phoneCount >= 2) {
                listOf(
                    RingtoneSlot(0, context.getString(R.string.sim_1)),
                    RingtoneSlot(1, context.getString(R.string.sim_2))
                )
            } else {
                listOf(RingtoneSlot(0, context.getString(R.string.sim_1)))
            }
        }

        return activeSlots.take(2).map { info ->
            val labelRes = if (info.simSlotIndex == 0) R.string.sim_1 else R.string.sim_2
            RingtoneSlot(
                slotIndex = info.simSlotIndex,
                label = context.getString(labelRes)
            )
        }
    }

    private data class RingtoneSlot(
        val slotIndex: Int,
        val label: String
    )

    private data class PendingRingtoneRequest(
        val music: Music,
        val slot: RingtoneSlot?
    )

    private val SIM1_RINGTONE_KEYS = listOf(
        "ringtone",
        "ringtone_1",
        "ringtone1",
        "ringtone_sim1",
        "ringtone_slot_1"
    )

    private val SIM2_RINGTONE_KEYS = listOf(
        "ringtone_2",
        "ringtone2",
        "ringtone_sim2",
        "ringtone_slot_2"
    )
}
