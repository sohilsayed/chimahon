package eu.kanade.tachiyomi.ui.player.controls

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import chimahon.MediaInfo
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPopupWebViewWarmup
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.dictionary.ScreenLookupOverlay
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun PlayerVideoOcrOverlay(
    viewModel: PlayerViewModel,
    screenshot: Bitmap?,
    onDismiss: () -> Unit,
    onRecapture: () -> Unit,
) {
    if (screenshot == null) return

    val context = LocalContext.current
    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val source by viewModel.currentSource.collectAsState()
    val anime by viewModel.currentAnime.collectAsState()
    val episode by viewModel.currentEpisode.collectAsState()
    val activeProfile = remember(source?.id, source?.lang) {
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

    ScreenLookupOverlay(
        screenshot = screenshot,
        webView = webView,
        activeProfile = activeProfile,
        onClose = onDismiss,
        onRecapture = onRecapture,
        type = "anime",
        mediaInfo = MediaInfo(
            mangaTitle = anime?.title.orEmpty(),
            chapterName = episode?.name.orEmpty(),
        ),
        titleId = anime?.id?.toString(),
        onRequestSentenceAudio = { viewModel.captureVideoOcrAudioForAnki() },
    )
}
