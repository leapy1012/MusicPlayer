package gd.app.musicplayer.playback

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class IndexActionData(val index: Int) : Parcelable
