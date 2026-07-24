package chimahon.audio

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class WordAudioSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    var isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val type: SourceType = SourceType.ONLINE,
) {
    enum class SourceType {
        LOCAL,
        ONLINE,
    }

    companion object {
        const val LOCAL_URL = "chimahon-local://android.db"

        fun createLocal() = WordAudioSource(
            id = "local_audio",
            name = "Local Audio",
            url = LOCAL_URL,
            type = SourceType.LOCAL,
        )
    }
}

data class WordAudioResult(
    val name: String,
    val url: String,
    val source: WordAudioSource,
    val data: ByteArray? = null, // For local audio bytes
)
