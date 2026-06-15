package eu.kanade.tachiyomi.ui.dictionary

import android.content.Intent

data class ProcessTextLookupRequest(
    val query: String,
) {
    companion object {
        fun fromIntent(intent: Intent?): ProcessTextLookupRequest? {
            if (intent?.action != Intent.ACTION_PROCESS_TEXT) return null
            val query = intent
                .getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                ?.toString()
                ?.trim()
                .orEmpty()
            return query.takeIf { it.isNotEmpty() }?.let(::ProcessTextLookupRequest)
        }
    }
}
