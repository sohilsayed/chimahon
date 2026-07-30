package eu.kanade.tachiyomi.ui.player.controls

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import chimahon.DictionaryRepository
import chimahon.MediaInfo
import chimahon.anki.AnkiProfile
import chimahon.anki.AnkiScreenshotMode
import chimahon.anki.Marker
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPopupWebViewWarmup
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryPaths
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.scene.SceneCaptureRequest
import eu.kanade.tachiyomi.ui.reader.viewer.OcrLookupPopup
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tachiyomi.i18n.kmk.KMR
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
    val sceneCaptureRequest: SceneCaptureRequest? = null,
)

@Composable
internal fun PlayerSubtitleLookupPopup(
    viewModel: PlayerViewModel,
    request: SubtitleLookupRequest?,
    sceneCapturePending: Boolean,
    onDismiss: () -> Unit,
    onTermMatched: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val repository = remember { Injekt.get<DictionaryRepository>() }
    val anime by viewModel.currentAnime.collectAsState()
    val episode by viewModel.currentEpisode.collectAsState()
    val source by viewModel.currentSource.collectAsState()
    val miningProgress by viewModel.sceneMiningProgress.collectAsState()
    val profileResolutionKey = DictionaryProfileResolutionKey(
        animeId = anime?.id,
        sourceId = source?.id,
        sourceLang = source?.lang,
    )
    val activeProfile = remember(profileResolutionKey) {
        dictionaryPreferences.profileResolver.resolveForPlayer(profileResolutionKey)
    }
    val webView: WebView = remember(activeProfile.languageCode) {
        DictionaryPopupWebViewWarmup.acquire(context, activeProfile.languageCode)
    }

    LaunchedEffect(activeProfile) {
        withContext(Dispatchers.IO) {
            repository.warmUp(getDictionaryPaths(context, activeProfile), activeProfile.id)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            DictionaryPopupWebViewWarmup.recycle(context, webView)
        }
    }

    BackHandler(enabled = request != null, onBack = onDismiss)

    val visible = request != null
    val sceneRequest = request?.sceneCaptureRequest
    val screenshotMode = AnkiScreenshotMode.fromStorageValue(activeProfile.ankiCropMode)
    val mediaRequest = remember(sceneRequest, screenshotMode) {
        viewModel.createSceneMediaRequest(
            request = sceneRequest,
            screenshotMode = screenshotMode.storageValue,
        )
    }
    val launchMiningJob: (suspend () -> Unit) -> Boolean = remember(sceneRequest) {
        { block -> viewModel.launchSceneMining(sceneRequest, block) }
    }

    OcrLookupPopup(
        visible = visible,
        lookupString = request?.lookupString.orEmpty(),
        fullText = request?.fullText.orEmpty(),
        charOffset = request?.charOffset ?: 0,
        onDismiss = onDismiss,
        webView = webView,
        repository = repository,
        anchorX = request?.anchorX ?: 0f,
        anchorY = request?.anchorY ?: 0f,
        anchorWidth = request?.anchorWidth ?: 0f,
        anchorHeight = request?.anchorHeight ?: 0f,
        isVertical = false,
        activeProfile = activeProfile,
        type = "anime",
        mediaInfo = MediaInfo(
            mangaTitle = anime?.title.orEmpty(),
            chapterName = episode?.name.orEmpty(),
        ),
        mediaRequest = mediaRequest,
        miningBusy = sceneCapturePending || miningProgress.isBusy,
        launchMiningJob = launchMiningJob,
        onMiningBusy = {
            context.toast(KMR.strings.anki_scene_busy)
        },
        onAnkiMediaWarnings = context::showPlayerAnkiMediaWarnings,
        usePopup = false,
        onTermMatched = onTermMatched,
        modifier = modifier,
        titleId = anime?.id?.toString(),
    )
}

/**
 * Capturing a scene touches MPV and is unnecessary for a dictionary-only lookup.
 */
internal fun AnkiProfile.requiresSceneMediaCapture(): Boolean {
    if (!ankiEnabled) return false

    return runCatching {
        val fieldMap = JSONObject(ankiFieldMap)
        val fieldValues = fieldMap.keys().asSequence()
            .map { key -> fieldMap.optString(key) }
            .toList()
        val capturesScreenshot =
            AnkiScreenshotMode.fromStorageValue(ankiCropMode) != AnkiScreenshotMode.NONE &&
                fieldValues.any { it.contains(Marker.SCREENSHOT) }
        val capturesSentenceAudio = fieldValues.any { it.contains(Marker.SENTENCE_AUDIO) }
        capturesScreenshot || capturesSentenceAudio
    }.getOrDefault(false)
}
