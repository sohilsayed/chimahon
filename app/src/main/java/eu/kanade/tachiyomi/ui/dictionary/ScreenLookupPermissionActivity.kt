package eu.kanade.tachiyomi.ui.dictionary

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class ScreenLookupPermissionActivity : BaseActivity() {

    private var returnedFromOverlaySettings = false
    private var projectionRequested = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenLookupService.start(this, result.resultCode, data)
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, this.contextStringResource(MR.strings.screen_lookup_capture_denied), Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setComposeContent {
            TachiyomiTheme {
                ScreenLookupPermissionContent(
                    onRequestOverlay = ::openOverlaySettings,
                    onCancel = ::finish,
                )
            }
        }

        if (Settings.canDrawOverlays(this)) {
            requestProjection()
        }
    }

    override fun onResume() {
        super.onResume()
        if (returnedFromOverlaySettings && Settings.canDrawOverlays(this)) {
            returnedFromOverlaySettings = false
            requestProjection()
        }
    }

    private fun openOverlaySettings() {
        returnedFromOverlaySettings = true
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        runCatching {
            startActivity(intent)
        }.recoverCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.onFailure {
            returnedFromOverlaySettings = false
            Toast.makeText(this, this.contextStringResource(MR.strings.screen_lookup_overlay_required), Toast.LENGTH_LONG).show()
        }
    }

    private fun requestProjection() {
        if (projectionRequested) return
        projectionRequested = true
        val mediaProjectionManager = getSystemService<MediaProjectionManager>()
        if (mediaProjectionManager == null) {
            Toast.makeText(this, this.contextStringResource(MR.strings.screen_lookup_unavailable), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        projectionLauncher.launch(mediaProjectionManager.createScreenLookupCaptureIntent())
    }

    private fun MediaProjectionManager.createScreenLookupCaptureIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
        } else {
            createScreenCaptureIntent()
        }
    }
}

@Composable
private fun ScreenLookupPermissionContent(
    onRequestOverlay: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(MR.strings.screen_lookup_enable_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(MR.strings.screen_lookup_enable_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(MR.strings.action_cancel))
                }
                Button(onClick = onRequestOverlay) {
                    Text(stringResource(MR.strings.action_open_settings))
                }
            }
        }
    }
}
