package mihon.feature.trackadd

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.manga.track.TrackerSearchScreen
import mihon.feature.trackadd.components.TrackAddConfirmDialog
import mihon.feature.trackadd.components.TrackAddExitDialog
import mihon.feature.trackadd.components.TrackAddProgressDialog
import uy.kohesive.injekt.injectLazy

class TrackAddScreen(
    private val mangaIds: Collection<Long>,
    private val trackerId: Long,
) : Screen() {

    private val trackerManager: TrackerManager by injectLazy()

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val tracker = remember(trackerId) { trackerManager.get(trackerId) } ?: return
        val screenModel = rememberScreenModel { TrackAddScreenModel(mangaIds, tracker) }
        val state by screenModel.state.collectAsState()

        LaunchedEffect(screenModel) {
            screenModel.navigateBackEvent.collect {
                navigator.pop()
            }
        }

        TrackAddScreenContent(
            items = state.items,
            allComplete = state.allComplete,
            finishedCount = state.finishedCount,
            trackerName = tracker.name,
            onSearchManually = { item ->
                navigator.push(
                    TrackerSearchScreen(
                        mangaId = item.manga.id,
                        initialQuery = item.manga.title,
                        currentUrl = null,
                        serviceId = trackerId,
                        onResult = { trackSearch ->
                            screenModel.updateManualResult(item.manga.id, trackSearch)
                        },
                    )
                )
            },
            onCancelItem = screenModel::cancelItem,
            onRemoveItem = screenModel::removeItem,
            onTrackAll = screenModel::showConfirmDialog,
        )

        when (val dialog = state.dialog) {
            is TrackAddScreenModel.Dialog.Confirm -> {
                TrackAddConfirmDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    totalCount = dialog.totalCount,
                    skippedCount = dialog.skippedCount,
                    onConfirm = screenModel::trackAll,
                )
            }
            is TrackAddScreenModel.Dialog.Progress -> {
                TrackAddProgressDialog(
                    progress = dialog.progress,
                    exitTrackAdd = screenModel::cancelTrackAll,
                )
            }
            TrackAddScreenModel.Dialog.Exit -> {
                TrackAddExitDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    exitTrackAdd = { navigator.pop() },
                )
            }
            null -> Unit
        }

        BackHandler(true) {
            screenModel.showExitDialog()
        }
    }
}
