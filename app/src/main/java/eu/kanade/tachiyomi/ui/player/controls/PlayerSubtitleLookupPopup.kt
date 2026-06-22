package eu.kanade.tachiyomi.ui.player.controls

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import chimahon.DictionaryRepository
import chimahon.MediaInfo
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPopupWebViewWarmup
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.reader.viewer.OcrLookupPopup
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal data class SubtitleLookupRequest(
    val lookupString: String,
    val fullText: String,
    val charOffset: Int,
    val tapCharOffset: Int,
    val lineText: String,
    val lineIndex: Int,
    val lineStartOffset: Int,
    val anchorX: Float,
    val anchorY: Float,
    val anchorWidth: Float,
    val anchorHeight: Float,
    val lineLeft: Float,
    val lineTop: Float,
    val lineWidth: Float,
    val lineHeight: Float,
    val matchedCharCount: Int = 0,
    val matchOffset: Int = 0,
    val cueStartSeconds: Double? = null,
    val cueEndSeconds: Double? = null,
)

@Composable
internal fun PlayerSubtitleLookupPopup(
    viewModel: PlayerViewModel,
    request: SubtitleLookupRequest?,
    onDismiss: () -> Unit,
    onTermMatched: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (request == null) return

    val context = LocalContext.current
    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val repository = remember { Injekt.get<DictionaryRepository>() }
    val anime by viewModel.currentAnime.collectAsState()
    val episode by viewModel.currentEpisode.collectAsState()
    val source by viewModel.currentSource.collectAsState()
    val activeProfile = remember(anime?.id, source?.id, source?.lang) {
        dictionaryPreferences.profileResolver.resolve(
            sourceId = source?.id ?: 0L,
            sourceLang = source?.lang.orEmpty(),
        )
    }
    val webView: WebView = remember(activeProfile.languageCode) {
        DictionaryPopupWebViewWarmup.acquire(context, activeProfile.languageCode)
    }

    DisposableEffect(webView) {
        onDispose {
            DictionaryPopupWebViewWarmup.recycle(context, webView)
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
        )
        OcrLookupPopup(
            visible = true,
            lookupString = request.lookupString,
            fullText = request.fullText,
            charOffset = request.charOffset,
            onDismiss = onDismiss,
            webView = webView,
            repository = repository,
            anchorX = request.anchorX,
            anchorY = request.anchorY,
            anchorWidth = request.anchorWidth,
            anchorHeight = request.anchorHeight,
            isVertical = false,
            activeProfile = activeProfile,
            type = "anime",
            mediaInfo = MediaInfo(
                mangaTitle = anime?.title.orEmpty(),
                chapterName = episode?.name.orEmpty(),
            ),
            onRequestScreenshot = {
                viewModel.captureVideoFrameForOcr()
            },
            onRequestSentenceAudio = {
                viewModel.captureSubtitleAudioForAnki(
                    startSeconds = request.cueStartSeconds,
                    endSeconds = request.cueEndSeconds,
                )
            },
            usePopup = false,
            onTermMatched = onTermMatched,
            modifier = modifier,
            titleId = anime?.id?.toString(),
        )
    }
    }
