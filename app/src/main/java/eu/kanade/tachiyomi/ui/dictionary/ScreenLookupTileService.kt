package eu.kanade.tachiyomi.ui.dictionary

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

class ScreenLookupTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (ScreenLookupServiceState.isRunning.value) {
            ScreenLookupService.stop(this)
            updateTile(active = false)
            return
        }

        startPermissionActivity()
    }

    private fun startPermissionActivity() {
        val intent = Intent(this, ScreenLookupPermissionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(active: Boolean = ScreenLookupServiceState.isRunning.value) {
        qsTile?.apply {
            label = this@ScreenLookupTileService.stringResource(MR.strings.screen_lookup_title)
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, ScreenLookupTileService::class.java),
                )
            }
        }
    }
}
