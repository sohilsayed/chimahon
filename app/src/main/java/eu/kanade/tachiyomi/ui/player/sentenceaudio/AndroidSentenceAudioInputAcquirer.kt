package eu.kanade.tachiyomi.ui.player.sentenceaudio

import android.content.Context
import android.net.Uri
import java.io.File

internal class AndroidSentenceAudioInputAcquirer(
    context: Context,
) : SentenceAudioInputAcquirer {
    private val applicationContext = context.applicationContext
    private val caBundle = File(applicationContext.filesDir, "cacert.pem")

    override suspend fun acquire(input: SentenceAudioInputSpec): SentenceAudioInputLease? = when (input.kind) {
        SentenceAudioInputKind.CONTENT_URI -> acquireContentUri(input.value)
        SentenceAudioInputKind.REMOTE_HTTP -> getCaBundle()?.let { lease(input.value, it.absolutePath) }
        SentenceAudioInputKind.LOCAL_FILE -> lease(input.value)
    }

    private fun getCaBundle(): File? = synchronized(caBundleLock) {
        caBundle.takeIf { it.isFile && it.canRead() && it.length() > 0L } ?: runCatching {
            applicationContext.assets.open("cacert.pem").use { input ->
                caBundle.outputStream().use(input::copyTo)
            }
            caBundle.takeIf { it.isFile && it.canRead() && it.length() > 0L }
        }.getOrNull()
    }

    private fun acquireContentUri(value: String): SentenceAudioInputLease? {
        val descriptor = runCatching {
            applicationContext.contentResolver.openFileDescriptor(Uri.parse(value), "r")
        }.getOrNull() ?: return null
        return object : SentenceAudioInputLease {
            override val ffmpegValue = "/proc/self/fd/${descriptor.fd}"
            override val tlsCaFile: String? = null
            override fun close() = descriptor.close()
        }
    }

    private fun lease(value: String, caFile: String? = null): SentenceAudioInputLease = object : SentenceAudioInputLease {
        override val ffmpegValue = value
        override val tlsCaFile = caFile
        override fun close() = Unit
    }

    private companion object {
        val caBundleLock = Any()
    }
}
