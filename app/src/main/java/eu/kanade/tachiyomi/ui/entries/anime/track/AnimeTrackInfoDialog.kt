package eu.kanade.tachiyomi.ui.entries.anime.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class AnimeTrackInfoDialogHomeScreen(
    private val animeId: Long,
    private val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel { Model(animeId, sourceId) }
        val state by screenModel.state.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.trackItems.forEachIndexed { index, item ->
                AnimeTrackInfoItem(
                    item = item,
                    onBindEnhanced = {
                        screenModel.bindEnhanced()
                        context.toast(context.contextStringResource(MR.strings.trackers_updated_summary_anime))
                    },
                    onOpen = {
                        item.track?.remoteUrl
                            ?.takeIf(String::isNotBlank)
                            ?.let(context::openInBrowser)
                    },
                )
                if (index != state.trackItems.lastIndex) {
                    HorizontalDivider()
                }
            }
            if (state.trackItems.isEmpty()) {
                Text(
                    text = "Log in to a tracker to track anime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        screenModel.refreshTrackers { hasFailures ->
                            context.toast(
                                context.contextStringResource(
                                    if (hasFailures) {
                                        MR.strings.internal_error
                                    } else {
                                        MR.strings.trackers_updated_summary_anime
                                    },
                                ),
                            )
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_webview_refresh))
                }
            }
        }
    }

    private class Model(
        private val animeId: Long,
        private val sourceId: Long,
        private val getAnime: GetAnime = Injekt.get(),
        private val animeSourceManager: AnimeSourceManager = Injekt.get(),
        private val trackerManager: TrackerManager = Injekt.get(),
        private val animeTrackRepository: AnimeTrackRepository = Injekt.get(),
        private val addTracks: AddAnimeTracks = Injekt.get(),
        private val refreshAnimeTracks: RefreshAnimeTracks = Injekt.get(),
    ) : StateScreenModel<State>(State()) {

        init {
            screenModelScope.launch {
                combine(
                    animeTrackRepository.getTracksByAnimeIdAsFlow(animeId).catch { logcat(LogPriority.ERROR, it) },
                    trackerManager.loggedInAnimeTrackersFlow(),
                ) { animeTracks, loggedInTrackers ->
                    loggedInTrackers
                        .filterIsInstance<BaseTracker>()
                        .filter { it is AnimeTracker }
                        .filter { tracker ->
                            (tracker as? EnhancedAnimeTracker)?.accept(animeSourceManager.getOrStub(sourceId)) ?: true
                        }
                        .map { tracker ->
                            TrackItem(
                                track = animeTracks.find { it.trackerId == tracker.id },
                                tracker = tracker,
                            )
                        }
                }.collectLatest { mutableState.value = State(it) }
            }
        }

        fun bindEnhanced() {
            screenModelScope.launch {
                val anime = getAnime.await(animeId) ?: return@launch
                addTracks.bindEnhancedTrackers(anime, animeSourceManager.getOrStub(sourceId))
            }
        }

        fun refreshTrackers(onComplete: (hasFailures: Boolean) -> Unit) {
            screenModelScope.launch {
                val hasFailures = try {
                    refreshAnimeTracks.await(animeId).isNotEmpty()
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e)
                    true
                }
                onComplete(hasFailures)
            }
        }
    }

    private data class State(
        val trackItems: List<TrackItem> = emptyList(),
    )
}

@Composable
private fun AnimeTrackInfoItem(
    item: TrackItem,
    onBindEnhanced: () -> Unit,
    onOpen: () -> Unit,
) {
    val tracker = item.tracker
    val animeTracker = tracker as? AnimeTracker
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tracker.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.track?.title ?: stringResource(SYMR.strings.not_tracked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.track == null && tracker is EnhancedAnimeTracker) {
                Button(onClick = onBindEnhanced) {
                    Text(text = stringResource(MR.strings.action_track))
                }
            } else if (item.track?.remoteUrl?.isNotBlank() == true) {
                OutlinedButton(onClick = onOpen) {
                    Text(text = stringResource(MR.strings.action_open_in_browser))
                }
            }
        }
        item.track?.let { track ->
            Text(
                text = buildString {
                    animeTracker?.getStatusForAnime(track.status)?.let { append(stringResource(it)) }
                    if (isNotEmpty()) append("  ")
                    append(track.lastEpisodeSeen.toInt())
                    if (track.totalEpisodes > 0) append(" / ${track.totalEpisodes}")
                    if (track.score > 0.0) append("  ${animeTracker?.displayScore(track).orEmpty()}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
