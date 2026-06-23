package eu.kanade.tachiyomi.util.storage

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.hippo.unifile.UniFile

fun Uri.toFFmpegString(context: Context): String {
    val raw = if (this.scheme == "content") {
        FFmpegKitConfig.getSafParameter(context, this, "rw") ?: this.path
    } else {
        this.path
    }
    return raw?.replace("\"", "\\\"") ?: ""
}

fun UniFile.toFFmpegString(context: Context? = null): String {
    val raw = if (context != null && this.uri.scheme == "content") {
        FFmpegKitConfig.getSafParameter(context, this.uri, "rw") ?: this.filePath
    } else {
        this.filePath
    }
    return raw?.replace("\"", "\\\"") ?: ""
}
