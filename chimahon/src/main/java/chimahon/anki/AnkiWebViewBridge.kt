package chimahon.anki

import android.webkit.JavascriptInterface
import chimahon.LookupResult

class AnkiWebViewBridge(
    private val resultsProvider: () -> List<LookupResult>,
    private val onAddToAnki: (LookupResult) -> Unit,
) {
    @JavascriptInterface
    fun addToAnki(indexStr: String) {
        val index = indexStr.toIntOrNull() ?: return
        val results = resultsProvider()
        if (index in results.indices) {
            onAddToAnki(results[index])
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
    var onAddToAnki: ((LookupResult) -> Unit)? = null

    @JavascriptInterface
    fun addToAnki(indexStr: String) {
        val index = indexStr.toIntOrNull() ?: return
        val results = resultsProvider()
        if (index in results.indices) {
            onAddToAnki?.invoke(results[index])
        }
    }
}
