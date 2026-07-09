package tachiyomi.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.LifecycleCoroutineScope
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.updates.interactor.GetUpdates

class WidgetManager(
    private val getUpdates: GetUpdates,
    private val securityPreferences: SecurityPreferences,
) {

    fun Context.init(scope: LifecycleCoroutineScope) {
        // Separate collectors so lock toggles always refresh even when update IDs are unchanged.
        getUpdates.subscribe(read = false, after = BaseUpdatesGridGlanceWidget.DateLimit.toEpochMilli())
            .map { list -> list.map { it.chapterId }.toSet() }
            .distinctUntilChanged()
            .onEach { updateWidgets() }
            .flowOn(Dispatchers.Default)
            .launchIn(scope)

        securityPreferences.useAuthenticator()
            .changes()
            .distinctUntilChanged()
            .onEach { updateWidgets() }
            .flowOn(Dispatchers.Default)
            .launchIn(scope)
    }

    private suspend fun Context.updateWidgets() {
        try {
            UpdatesGridGlanceWidget().updateAll(this)
            UpdatesGridCoverScreenGlanceWidget().updateAll(this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update widget" }
        }
    }
}
