package chimahon.anki

import android.webkit.JavascriptInterface
import chimahon.GlossaryEntry
import chimahon.LookupResult

class AnkiWebViewBridge(
    private val resultsProvider: () -> List<LookupResult>,
    private val onAddToAnki: (LookupResult, Int?) -> Unit,
) {
    @JavascriptInterface
    fun addToAnki(resultIndexStr: String, glossaryIndexStr: String) {
        val resultIndex = resultIndexStr.toIntOrNull() ?: return
        val glossaryIndex = glossaryIndexStr.toIntOrNull()
        val results = resultsProvider()
        if (resultIndex in results.indices) {
            onAddToAnki(results[resultIndex], glossaryIndex)
        }
    }
}

/**
 * Stable bridge registered on the WebView once.
 * Delegates to mutable callbacks that are updated on recompose.
 * This avoids the WebView holding a stale bridge reference.
 */
class DelegatingWebViewBridge(
    private val resultsProvider: () -> List<LookupResult>,
) {
    var onAddToAnki: ((LookupResult, Int?) -> Unit)? = null

    @JavascriptInterface
    fun addToAnki(resultIndexStr: String, glossaryIndexStr: String) {
        val resultIndex = resultIndexStr.toIntOrNull() ?: return
        val glossaryIndex = glossaryIndexStr.toIntOrNull()
        val results = resultsProvider()
        if (resultIndex in results.indices) {
            onAddToAnki?.invoke(results[resultIndex], glossaryIndex)
        }
    }
}
