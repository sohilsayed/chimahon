package eu.kanade.presentation.more.settings.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.canopus.chimareader.ttusync.DriveAuthStatus
import com.canopus.chimareader.ttusync.TtuOAuthManager
import com.canopus.chimareader.ttusync.TtuSyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.CheckboxItem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TtuSyncSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val oauthManager = remember { Injekt.get<TtuOAuthManager>() }
    val syncManager = remember { Injekt.get<TtuSyncManager>() }
    val clipboardManager = LocalClipboardManager.current

    var clientId by remember { mutableStateOf(oauthManager.clientId) }
    var clientSecret by remember { mutableStateOf(oauthManager.clientSecret) }
    var authStatus by remember { mutableStateOf(
        if (oauthManager.isConnected) DriveAuthStatus.CONNECTED else DriveAuthStatus.NOT_CONFIGURED
    ) }
    var userCode by remember { mutableStateOf("") }
    var verificationUrl by remember { mutableStateOf("") }
    var polling by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var enabled by remember { mutableStateOf(syncManager.isEnabled) }
    var autoSync by remember { mutableStateOf(syncManager.autoSyncEnabled) }
    var statsSync by remember { mutableStateOf(syncManager.statisticsSyncEnabled) }
    var statsMode by remember { mutableStateOf(syncManager.statisticsSyncMode) }
    var audioSync by remember { mutableStateOf(syncManager.audioBookSyncEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "ッツ Novel Sync",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Sync reading progress, statistics, and audiobook position between " +
                "Chimahon and Hoshi Reader via Google Drive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        // Google Cloud Client ID
        OutlinedTextField(
            value = clientId,
            onValueChange = {
                clientId = it
                oauthManager.clientId = it
            },
            label = { Text("Google Client ID") },
            placeholder = { Text("e.g. 12345-abc.apps.googleusercontent.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = authStatus != DriveAuthStatus.CONNECTED,
        )

        Spacer(Modifier.height(8.dp))

        // Google Cloud Client Secret
        OutlinedTextField(
            value = clientSecret,
            onValueChange = {
                clientSecret = it
                oauthManager.clientSecret = it
            },
            label = { Text("Google Client Secret") },
            placeholder = { Text("e.g. GOCSPX-xxxxxxxxxxxx") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = authStatus != DriveAuthStatus.CONNECTED,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Create a \"TVs and Limited Input\" OAuth client in Google Cloud Console " +
                "using the same project as your ッツ Reader / Hoshi Reader setup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // Connect / Sign Out
        when (authStatus) {
            DriveAuthStatus.CONNECTED -> {
                OutlinedButton(
                    onClick = {
                        oauthManager.clearAuth()
                        authStatus = DriveAuthStatus.NOT_CONFIGURED
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign Out")
                }
            }

            DriveAuthStatus.AWAITING_CODE -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Enter this code at $verificationUrl",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(userCode))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy Code: $userCode")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verificationUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Verification URL")
                }
            }

            DriveAuthStatus.ERROR -> {
                OutlinedButton(
                    onClick = {
                        authStatus = DriveAuthStatus.NOT_CONFIGURED
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Retry")
                }
            }

            else -> {
                Button(
                    onClick = {
                        if (clientId.isBlank()) {
                            errorMessage = "Client ID is required"
                            return@Button
                        }
                        errorMessage = null
                        polling = true
                        scope.launch {
                            try {
                                val result = oauthManager.requestDeviceCode()
                                userCode = result.userCode
                                verificationUrl = result.verificationUrl
                                authStatus = DriveAuthStatus.AWAITING_CODE

                                var interval = result.interval.coerceAtLeast(5)
                                while (true) {
                                    delay(interval * 1000L)
                                    try {
                                        if (oauthManager.pollAuthorization(result.deviceCode, interval)) {
                                            authStatus = DriveAuthStatus.CONNECTED
                                            polling = false
                                            break
                                        }
                                    } catch (e: com.canopus.chimareader.ttusync.DriveAuthException.AccessDenied) {
                                        errorMessage = "Access denied"
                                        authStatus = DriveAuthStatus.ERROR
                                        polling = false
                                        break
                                    } catch (e: com.canopus.chimareader.ttusync.DriveAuthException.ExpiredToken) {
                                        errorMessage = "Code expired, try again"
                                        authStatus = DriveAuthStatus.ERROR
                                        polling = false
                                        break
                                    } catch (e: Exception) {
                                        // continue polling on transient errors
                                        if (!oauthManager.isConnected) {
                                            interval = (interval + 5).coerceAtMost(30)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Authorization failed"
                                authStatus = DriveAuthStatus.ERROR
                                polling = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !polling && clientId.isNotBlank(),
                ) {
                    Icon(Icons.Outlined.CloudSync, contentDescription = null)
                    Text("Connect Google Drive")
                }
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (authStatus == DriveAuthStatus.CONNECTED) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            CheckboxItem(
                label = "Enable Sync",
                checked = enabled,
                onChecked = {
                    enabled = it
                    syncManager.setEnabled(it)
                },
            )

            if (enabled) {
                Spacer(Modifier.height(8.dp))

                CheckboxItem(
                    label = "Auto Sync",
                    checked = autoSync,
                    onChecked = {
                        autoSync = it
                        syncManager.autoSyncEnabled = it
                    },
                )
                Text(
                    text = "Automatically sync reading progress on page turns and reader close.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                CheckboxItem(
                    label = "Sync Reading Statistics",
                    checked = statsSync,
                    onChecked = {
                        statsSync = it
                        syncManager.statisticsSyncEnabled = it
                    },
                )

                if (statsSync) {
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Statistics Sync Mode",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.material3.FilterChip(
                                selected = statsMode == "Merge",
                                onClick = {
                                    statsMode = "Merge"
                                    syncManager.statisticsSyncMode = "Merge"
                                },
                                label = { Text("Merge") },
                            )
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.material3.FilterChip(
                                selected = statsMode == "Replace",
                                onClick = {
                                    statsMode = "Replace"
                                    syncManager.statisticsSyncMode = "Replace"
                                },
                                label = { Text("Replace") },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                CheckboxItem(
                    label = "Sync Audiobook Position",
                    checked = audioSync,
                    onChecked = {
                        audioSync = it
                        syncManager.audioBookSyncEnabled = it
                    },
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val books = com.canopus.chimareader.data.BookStorage
                                .loadAllBooks(context)
                            for (book in books) {
                                syncManager.syncBook(book)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sync All Books Now")
                }
            }
        }
    }
}
