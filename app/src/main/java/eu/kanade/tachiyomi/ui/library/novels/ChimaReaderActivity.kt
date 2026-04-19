package eu.kanade.tachiyomi.ui.library.novels

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import chimahon.DictionaryRepository
import com.canopus.chimareader.ui.reader.NovelReaderActivity
import eu.kanade.tachiyomi.ui.reader.viewer.OcrLookupPopup
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import logcat.LogPriority
import eu.kanade.tachiyomi.data.sync.ttsu.TtsuSyncManager
import com.canopus.chimareader.data.Statistics
import tachiyomi.core.common.util.system.logcat
import kotlinx.coroutines.withContext

/**
 * App-side subclass of [NovelReaderActivity] that wires text-selection events
 * from the EPUB reader WebView into the [OcrLookupPopup] — no screenshot needed,
 * no OCR bitmap involved: just a plain text selection → dictionary lookup.
 *
 * [NovelReaderActivity.activityClass] is pointed at this class from [App.onCreate]
 * so that [BookshelfScreen]'s existing `NovelReaderActivity.launch()` call
 * automatically lands here without any chimahon → app module import.
 */
class ChimaReaderActivity : NovelReaderActivity() {

    /**
     * Backing state for the lookup popup. Using Activity-level `mutableStateOf`
     * (not inside a composable) means it survives re-compositions correctly and
     * can be set from the non-Composable [onLookupRequested] override.
     */
    private var lookupState by mutableStateOf<LookupState?>(null)


    private data class LookupState(
        val word: String,
        val sentence: String,
        val anchorX: Float,
        val anchorY: Float,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    /** Called by [NovelReaderActivity] whenever the user selects text in the WebView. */
    override fun onLookupRequested(word: String, sentence: String, x: Float, y: Float) {
        lookupState = LookupState(word, sentence, x, y)
        isPopupActive = true
    }

    /** Called by [NovelReaderActivity] when the reader is closed/disposed. */
    override fun onReaderClosed(bookTitle: String, progress: Double, charsRead: Int, timestamp: Long, statistics: List<Statistics>) {
        syncToGoogleDrive(bookTitle, progress, charsRead, timestamp, statistics)
    }

    /** Called by [NovelReaderActivity] periodically while reading. */
    override fun onPeriodicSync(bookTitle: String, progress: Double, charsRead: Int, timestamp: Long, statistics: List<Statistics>) {
        syncToGoogleDrive(bookTitle, progress, charsRead, timestamp, statistics)
    }

    private fun syncToGoogleDrive(bookTitle: String, progress: Double, charsRead: Int, timestamp: Long, statistics: List<Statistics>) {
        val syncManager = TtsuSyncManager(this)
        // Use GlobalScope + NonCancellable to ensure sync completes even if activity is destroyed
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                logcat(LogPriority.DEBUG, tag = "TTSU-SYNC") { "Starting sync for $bookTitle..." }
                
                val progressResult = syncManager.pushProgressToGoogleDrive(
                    bookTitle = bookTitle,
                    exploredCharCount = charsRead,
                    progress = progress,
                    lastModified = timestamp
                )
                logcat(LogPriority.DEBUG, tag = "TTSU-SYNC") { "Progress sync result: $progressResult" }

                val statsResult = syncManager.pushStatisticsToGoogleDrive(
                    bookTitle = bookTitle,
                    statistics = statistics
                )
                logcat(LogPriority.DEBUG, tag = "TTSU-SYNC") { "Statistics sync result: $statsResult" }
                
                logcat(LogPriority.INFO, tag = "TTSU-SYNC") { "Finished sync for $bookTitle." }
            }
        }
    }

    /**
     * Injected into the parent's `setContent {}` via the [PopupOverlay] hook.
     * Renders the lookup popup over the reader content when text is selected.
     * No screenshot / crop — those are OCR-only features.
     */
    @Composable
    override fun PopupOverlay() {
        val state = lookupState ?: return

        // Retain a single WebView + repository across re-compositions so the
        // current lookup result isn't destroyed on every keystroke / recompose.
        val externalFilesDir = getExternalFilesDir(null)
        val repo = remember { DictionaryRepository(externalFilesDir) }
        val webView = remember { WebView(this) }

        BackHandler {
            lookupState = null
            isPopupActive = false
        }

        // Background scrim to capture and consume clicks outside the popup.
        // This prevents the click from reaching the WebView and triggering navigation.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    lookupState = null
                    isPopupActive = false
                }
        )

        eu.kanade.presentation.theme.TachiyomiTheme {
            OcrLookupPopup(
                lookupString = state.word,
                fullText = state.sentence,
                charOffset = state.sentence.indexOf(state.word).coerceAtLeast(0),
                onDismiss = { 
                    lookupState = null
                    isPopupActive = false
                },
                webView = webView,
                repository = repo,
                anchorX = state.anchorX,
                anchorY = state.anchorY,
                // No screenshot — plain text selection only
                mediaInfo = null,
                onRequestScreenshot = null,
                onCropTriggered = null,
                modifier = Modifier,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The retained WebView must be destroyed with the Activity to avoid leaks
        lookupState = null
    }
}
