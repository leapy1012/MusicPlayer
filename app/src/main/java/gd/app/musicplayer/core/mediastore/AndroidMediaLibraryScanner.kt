package gd.app.musicplayer.core.mediastore

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.core.database.entity.MusicEntity
import gd.app.musicplayer.domain.repository.MediaLibraryScanner
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class AndroidMediaLibraryScanner @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val dispatchers: AppDispatchers
) : MediaLibraryScanner {

    private val importer = MediaStoreMusicImporter()

    override fun queryMusic(modifiedSinceMs: Long?): List<MusicEntity> {
        return importer.queryMusic(context = appContext, modifiedSinceMs = modifiedSinceMs)
    }

    override fun queryMusicIds(): Set<Long> {
        return importer.queryMusicIds(appContext)
    }

    override suspend fun scanAudioFiles(
        selectedPaths: List<String>,
        onFindingFile: suspend (String) -> Unit,
        onParseProgress: suspend (Int) -> Unit
    ): List<String> {
        val audioFiles = findAudioFiles(selectedPaths, onFindingFile)
        scanWithMediaScanner(audioFiles, onParseProgress)
        return audioFiles
    }

    private suspend fun findAudioFiles(
        selectedPaths: List<String>,
        onFindingFile: suspend (String) -> Unit
    ): List<String> = withContext(dispatchers.io) {
        val roots = selectedPaths
            .ifEmpty { defaultRoots() }
            .map(::File)
            .filter { file -> file.exists() && file.canRead() }
            .distinctBy { file -> file.absolutePath.normalizedPath() }

        val result = mutableListOf<String>()
        roots.forEach { root ->
            ensureActive()
            if (root.isFile) {
                if (root.isAudioFile()) {
                    result += root.absolutePath
                    onFindingFile(root.absolutePath)
                }
                return@forEach
            }

            root.walkTopDown()
                .onEnter { dir -> dir.canRead() && !dir.isHidden }
                .forEach { file ->
                    ensureActive()
                    if (file.isFile && file.isAudioFile()) {
                        result += file.absolutePath
                        onFindingFile(file.absolutePath)
                    }
                }
        }
        result
    }

    private suspend fun scanWithMediaScanner(
        files: List<String>,
        onParseProgress: suspend (Int) -> Unit
    ) {
        if (files.isEmpty()) return
        withContext(dispatchers.io) {
            var scanned = 0
            files.forEach { path ->
                ensureActive()
                scanSingleFile(path)
                scanned++
                onParseProgress((scanned * 100 / files.size).coerceIn(0, 100))
            }
        }
    }

    private suspend fun scanSingleFile(path: String) {
        suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(path),
                arrayOf(AUDIO_MIME_TYPE)
            ) { _, _ ->
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    private fun defaultRoots(): List<String> {
        val roots = mutableListOf<String>()
        runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) }
            .getOrNull()
            ?.absolutePath
            ?.let(roots::add)
        runCatching { Environment.getExternalStorageDirectory() }
            .getOrNull()
            ?.absolutePath
            ?.let(roots::add)
        return roots.distinctBy(String::normalizedPath)
    }

    private companion object {
        const val AUDIO_MIME_TYPE = "audio/*"
    }
}

/**
 * Filesystem scan allowlist aligned with original h7.b (~500 audio/project extensions).
 */
private val AUDIO_EXTENSIONS = setOf(
    "3ga",
    "4mp",
    "5xb",
    "5xe",
    "5xs",
    "669",
    "8svx",
    "a2b",
    "a2i",
    "a2m",
    "aa",
    "aa3",
    "aac",
    "aax",
    "aaxc",
    "ab",
    "abc",
    "abm",
    "ac3",
    "acd",
    "acd-bak",
    "acd-zip",
    "acm",
    "acp",
    "act",
    "adg",
    "adt",
    "adts",
    "adv",
    "afc",
    "agm",
    "agr",
    "aif",
    "aifc",
    "aiff",
    "aimppl",
    "akp",
    "alc",
    "all",
    "als",
    "amf",
    "amr",
    "ams",
    "amxd",
    "amz",
    "ang",
    "aob",
    "ape",
    "apl",
    "aria",
    "ariax",
    "asd",
    "at3",
    "au",
    "aud",
    "aup",
    "aup3",
    "avastsounds",
    "ay",
    "b4s",
    "band",
    "bank",
    "bap",
    "bdd",
    "bidule",
    "bnk",
    "bnl",
    "brstm",
    "bun",
    "bwf",
    "bwg",
    "bww",
    "caf",
    "caff",
    "capobundle",
    "cda",
    "cdda",
    "cdo",
    "cdr",
    "cel",
    "cfa",
    "cgrp",
    "cidb",
    "ckb",
    "ckf",
    "conform",
    "copy",
    "cpr",
    "cpt",
    "csh",
    "cts",
    "cwb",
    "cwp",
    "cws",
    "cwt",
    "dcf",
    "dcm",
    "dct",
    "dewf",
    "df2",
    "dfc",
    "dff",
    "dig",
    "dls",
    "dm",
    "dmc",
    "dmf",
    "dmsa",
    "dmse",
    "dpdoc",
    "dra",
    "drg",
    "ds",
    "ds2",
    "dsf",
    "dsm",
    "dss",
    "dtm",
    "dts",
    "dtshd",
    "dvf",
    "dwd",
    "ec3",
    "efa",
    "efk",
    "efq",
    "efs",
    "efv",
    "emd",
    "emp",
    "emx",
    "esps",
    "expressionmap",
    "exs",
    "f2r",
    "f32",
    "f4a",
    "f64",
    "fdp",
    "fev",
    "flac",
    "flm",
    "flp",
    "fpa",
    "frg",
    "fsc",
    "fsm",
    "ftm",
    "ftmx",
    "fzf",
    "fzv",
    "g721",
    "g723",
    "g726",
    "gbproj",
    "gbs",
    "gig",
    "gp",
    "gp5",
    "gpbank",
    "gpk",
    "gpx",
    "groove",
    "gsf",
    "gsflib",
    "gsm",
    "h4b",
    "h5b",
    "h5e",
    "h5s",
    "hbe",
    "hca",
    "hsb",
    "iaa",
    "ics",
    "iff",
    "igp",
    "igr",
    "ins",
    "isma",
    "iti",
    "itls",
    "jam",
    "jbx",
    "jspf",
    "k26",
    "kar",
    "kfn",
    "kmp",
    "koala",
    "koz",
    "krz",
    "ksc",
    "ksf",
    "kt3",
    "l",
    "la",
    "lof",
    "logic",
    "logicx",
    "lso",
    "lwv",
    "m3u",
    "m3u8",
    "m4a",
    "m4b",
    "m4p",
    "m4r",
    "m5p",
    "ma1",
    "mbr",
    "mdc",
    "mdr",
    "med",
    "mgv",
    "mid",
    "midi",
    "minigsf",
    "minipsf",
    "minipsf2",
    "miniusf",
    "mka",
    "mmf",
    "mmlp",
    "mmm",
    "mmp",
    "mmpz",
    "mo3",
    "mod",
    "mogg",
    "mp2",
    "mp3",
    "mpa",
    "mpc",
    "mpdp",
    "mpga",
    "mpu",
    "mscz",
    "msmpl_bank",
    "mt2",
    "mte",
    "mtf",
    "mti",
    "mtm",
    "mtp",
    "mts",
    "mui",
    "mus",
    "musx",
    "mux",
    "mx3",
    "mx4",
    "mx5",
    "mx5template",
    "mxl",
    "mxmf",
    "myr",
    "narrative",
    "nbs",
    "ncw",
    "nkb",
    "nkc",
    "nki",
    "nkm",
    "nks",
    "nkx",
    "nml",
    "nmsv",
    "note",
    "npl",
    "nra",
    "nrt",
    "nsa",
    "ntn",
    "nvf",
    "nwc",
    "obw",
    "odm",
    "ofr",
    "oga",
    "ogg",
    "okt",
    "oma",
    "omf",
    "omg",
    "omx",
    "opus",
    "ots",
    "ove",
    "ovw",
    "pac",
    "pandora",
    "pbf",
    "pca",
    "pcast",
    "pcg",
    "peak",
    "pek",
    "pho",
    "phy",
    "pk",
    "pkf",
    "pla",
    "ply",
    "pna",
    "pno",
    "ppcx",
    "prg",
    "psf",
    "psf1",
    "psf2",
    "psm",
    "psy",
    "ptcop",
    "ptf",
    "ptm",
    "pts",
    "ptt",
    "ptx",
    "ptxt",
    "pvc",
    "q1",
    "qcp",
    "r1m",
    "ra",
    "rad",
    "ram",
    "raw",
    "rax",
    "rbs",
    "rcd",
    "rcy",
    "rdvxz",
    "repeaks",
    "rex",
    "rfl",
    "rgrp",
    "rip",
    "rmi",
    "rmj",
    "rmx",
    "rng",
    "rns",
    "rol",
    "rpl",
    "rsn",
    "rso",
    "rta",
    "rti",
    "rts",
    "rvx",
    "rx2",
    "s3i",
    "s3m",
    "s3z",
    "saf",
    "sap",
    "sbi",
    "sbk",
    "sc2",
    "scs11",
    "sd",
    "sd2",
    "sd2f",
    "sdat",
    "sds",
    "sdt",
    "seq",
    "ses",
    "sesx",
    "sf2",
    "sfap0",
    "sfk",
    "sfl",
    "sfpack",
    "sfs",
    "sfz",
    "sgp",
    "shn",
    "sib",
    "slp",
    "slx",
    "sma",
    "smf",
    "smp",
    "smpx",
    "snd",
    "sng",
    "sngx",
    "sns",
    "song",
    "sou",
    "sph",
    "sppack",
    "sseq",
    "ssm",
    "ssnd",
    "stap",
    "stm",
    "stx",
    "sty",
    "svd",
    "svp",
    "svx",
    "swa",
    "sxt",
    "syh",
    "syn",
    "syw",
    "syx",
    "tak",
    "td0",
    "tg",
    "toc",
    "trak",
    "tta",
    "txw",
    "u",
    "uax",
    "ult",
    "uni",
    "usf",
    "usflib",
    "ust",
    "uw",
    "uwf",
    "vag",
    "vap",
    "vb",
    "vc3",
    "vdj",
    "vgm",
    "vgz",
    "vip",
    "vlc",
    "vmd",
    "vmf",
    "vmo",
    "voc",
    "vox",
    "voxal",
    "vpl",
    "vpm",
    "vpr",
    "vpw",
    "vqf",
    "vrf",
    "vsq",
    "vsqx",
    "vtx",
    "vyf",
    "w01",
    "w64",
    "wav",
    "wave",
    "wax",
    "weba",
    "wfb",
    "wfd",
    "wfm",
    "wfp",
    "wma",
    "wow",
    "wpk",
    "wpp",
    "wproj",
    "wrk",
    "wtpl",
    "wtpt",
    "wus",
    "wut",
    "wv",
    "wvc",
    "wve",
    "wwu",
    "xa",
    "xfs",
    "xm",
    "xmf",
    "xmu",
    "xrns",
    "xsp",
    "xspf",
    "yookoo",
    "zpa",
    "zpl",
    "zvd",
)

private fun File.isAudioFile(): Boolean {
    return extension.lowercase() in AUDIO_EXTENSIONS
}

private fun String.normalizedPath(): String {
    return replace('\\', '/').trimEnd('/')
}
