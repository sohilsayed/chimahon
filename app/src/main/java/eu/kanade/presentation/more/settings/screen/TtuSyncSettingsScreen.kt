package eu.kanade.presentation.more.settings.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.canopus.chimareader.ttusync.DriveAuthStatus
import com.canopus.chimareader.ttusync.DriveAuthorizationPollResult
import com.canopus.chimareader.ttusync.DeviceCodePrompt
import com.canopus.chimareader.ttusync.GoogleCloudOAuthConfiguration
import com.canopus.chimareader.ttusync.StatisticsSyncMode
import com.canopus.chimareader.ttusync.SyncMode
import com.canopus.chimareader.ttusync.TtuOAuthManager
import com.canopus.chimareader.ttusync.TtuSyncManager
import com.canopus.chimareader.ttusync.nextDeviceCodePollIntervalSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TtuSyncSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val oauthManager = remember { Injekt.get<TtuOAuthManager>() }
    val syncManager = remember { Injekt.get<TtuSyncManager>() }
    val settings by syncManager.settingsFlow.collectAsState(initial = syncManager.loadSettings())

    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var authStatus by remember { mutableStateOf<DriveAuthStatus?>(null) }
    var directionMenuExpanded by remember { mutableStateOf(false) }
    var isAuthorizing by remember { mutableStateOf(false) }
    var devicePrompt by remember { mutableStateOf<DeviceCodePrompt?>(null) }
    var pollIntervalSeconds by remember { mutableStateOf(5L) }
    var message by remember { mutableStateOf<String?>(null) }
    var copyMessage by remember { mutableStateOf<String?>(null) }
    var statsModeMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(oauthManager) {
        clientId = oauthManager.clientId
        clientSecret = oauthManager.clientSecret
        authStatus = when {
            !oauthManager.isConfigured -> DriveAuthStatus.MissingConfiguration
            oauthManager.isConnected -> DriveAuthStatus.Connected
            else -> DriveAuthStatus.NotConnected
        }
    }

    LaunchedEffect(devicePrompt, isAuthorizing) {
        val prompt = devicePrompt ?: return@LaunchedEffect
        if (!isAuthorizing) return@LaunchedEffect
        val expiresAtMillis = System.currentTimeMillis() + prompt.expiresInSeconds * 1000L
        var nextIntervalSeconds = pollIntervalSeconds
        while (isAuthorizing && System.currentTimeMillis() < expiresAtMillis) {
            delay(nextIntervalSeconds * 1000L)
            val result = try {
                oauthManager.pollAuthorization(prompt)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                DriveAuthorizationPollResult.Failed(error.message ?: "Authorization failed")
            }
            when (result) {
                is DriveAuthorizationPollResult.Authorized -> {
                    isAuthorizing = false
                    devicePrompt = null
                    authStatus = DriveAuthStatus.Connected
                    message = null
                }
                DriveAuthorizationPollResult.Pending -> Unit
                DriveAuthorizationPollResult.SlowDown -> {
                    nextIntervalSeconds = nextDeviceCodePollIntervalSeconds(nextIntervalSeconds, result)
                    pollIntervalSeconds = nextIntervalSeconds
                }
                DriveAuthorizationPollResult.TransientNetworkFailure -> {
                    nextIntervalSeconds = nextDeviceCodePollIntervalSeconds(nextIntervalSeconds, result)
                    pollIntervalSeconds = nextIntervalSeconds
                    message = "Network issue. Open ${prompt.verificationUrl} and enter ${prompt.userCode} if needed."
                }
                is DriveAuthorizationPollResult.Failed -> {
                    isAuthorizing = false
                    devicePrompt = null
                    authStatus = DriveAuthStatus.Failed(result.message)
                    message = result.message
                }
            }
        }
        if (isAuthorizing) {
            isAuthorizing = false
            devicePrompt = null
            authStatus = DriveAuthStatus.NotConnected
            message = "Authorization code expired."
        }
    }

    fun connectGoogleDrive() {
        if (isAuthorizing) return
        isAuthorizing = true
        message = null
        copyMessage = null
        devicePrompt = null
        scope.launch {
            if (clientId.isBlank() || clientSecret.isBlank()) {
                isAuthorizing = false
                authStatus = DriveAuthStatus.MissingConfiguration
                message = "Configure a Google OAuth client before connecting."
                return@launch
            }
            oauthManager.saveClient(clientId, clientSecret)
            runCatching { oauthManager.requestDeviceCode() }
                .onSuccess { prompt ->
                    pollIntervalSeconds = prompt.intervalSeconds
                    devicePrompt = prompt
                    authStatus = DriveAuthStatus.NotConnected
                    message = "Open ${prompt.verificationUrl} and enter ${prompt.userCode}."
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(prompt.verificationUrl)))
                    }
                }
                .onFailure { error ->
                    isAuthorizing = false
                    val text = error.message ?: "Authorization failed"
                    authStatus = DriveAuthStatus.Failed(text)
                    message = text
                }
        }
    }

    fun signOut() {
        scope.launch {
            oauthManager.revokeAccess()
            syncManager.clearCache()
            authStatus = run {
                if (!oauthManager.isConfigured) DriveAuthStatus.MissingConfiguration
                else DriveAuthStatus.NotConnected
            }
            message = null
            copyMessage = null
            devicePrompt = null
            isAuthorizing = false
        }
    }

    val connectionActions = authStatus?.let { aa ->
        when (aa) {
            DriveAuthStatus.Connected -> SyncConnectionActions(
                showConnect = false, connectEnabled = false,
                showSignOut = true, signOutEnabled = !isAuthorizing,
            )
            DriveAuthStatus.MissingConfiguration -> SyncConnectionActions(
                showConnect = true, connectEnabled = !isAuthorizing,
                showSignOut = false, signOutEnabled = false,
            )
            DriveAuthStatus.NotConnected, is DriveAuthStatus.Failed -> SyncConnectionActions(
                showConnect = true, connectEnabled = !isAuthorizing,
                showSignOut = false, signOutEnabled = false,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "\u30C3\u30C4 Novel Sync",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(18.dp))

        // Enable / Direction / Auto Sync card
        SettingsCard {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Enable") },
                trailingContent = {
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { checked ->
                            syncManager.updateSettings { s -> s.copy(enabled = checked) }
                        },
                    )
                },
            )
            SettingsDivider()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Direction") },
                trailingContent = {
                    Box {
                        TextButton(onClick = { directionMenuExpanded = true }) {
                            Text(settings.mode.rawValue)
                        }
                        DropdownMenu(
                            expanded = directionMenuExpanded,
                            onDismissRequest = { directionMenuExpanded = false },
                        ) {
                            SyncMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.rawValue) },
                                    onClick = {
                                        directionMenuExpanded = false
                                        syncManager.updateSettings { s -> s.copy(mode = mode) }
                                    },
                                )
                            }
                        }
                    }
                },
            )
            SettingsDivider()
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Auto Sync") },
                trailingContent = {
                    Switch(
                        checked = settings.autoSyncEnabled,
                        onCheckedChange = { checked ->
                            syncManager.updateSettings { s -> s.copy(autoSyncEnabled = checked) }
                        },
                    )
                },
            )
        }

        Spacer(Modifier.height(18.dp))

        // Google Drive credentials card
        SettingsCard {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Google Drive") },
                supportingContent = {
                    Text(
                        text = when (authStatus) {
                            DriveAuthStatus.Connected -> "Connected"
                            DriveAuthStatus.NotConnected -> "Not connected"
                            DriveAuthStatus.MissingConfiguration -> "OAuth client not configured"
                            is DriveAuthStatus.Failed -> (authStatus as DriveAuthStatus.Failed).message
                            null -> ""
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingsDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Device client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text("Device client secret") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Device code prompt
        devicePrompt?.let { prompt ->
            Spacer(Modifier.height(18.dp))
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Authorize Google Drive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Open the link below and enter the device code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = prompt.verificationUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(prompt.verificationUrl)))
                        },
                    )
                    Text(
                        text = prompt.userCode,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Google device code", prompt.userCode))
                            copyMessage = "Device code copied"
                        },
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Copy code")
                    }
                }
            }
        }

        // Copy confirmation message
        copyMessage?.let { text ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Status message
        message?.let { text ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                color = if (authStatus is DriveAuthStatus.Failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Connect / Sign Out buttons
        connectionActions?.let { actions ->
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (actions.showConnect) {
                    Button(
                        onClick = { connectGoogleDrive() },
                        enabled = actions.connectEnabled && clientId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.CloudSync, contentDescription = null)
                        Text("Connect Google Drive")
                    }
                }
                if (actions.showSignOut) {
                    OutlinedButton(
                        onClick = { signOut() },
                        enabled = actions.signOutEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sign out")
                    }
                }
            }
        }

        // Sync settings (visible when connected and enabled)
        if (authStatus is DriveAuthStatus.Connected && settings.enabled) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Auto Sync") },
                supportingContent = { Text("Sync progress automatically while reading") },
                trailingContent = {
                    Switch(
                        checked = settings.autoSyncEnabled,
                        onCheckedChange = { checked ->
                            syncManager.updateSettings { s -> s.copy(autoSyncEnabled = checked) }
                        },
                    )
                },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Sync Reading Statistics") },
                trailingContent = {
                    Switch(
                        checked = settings.statisticsSyncEnabled,
                        onCheckedChange = { checked ->
                            syncManager.updateSettings { s -> s.copy(statisticsSyncEnabled = checked) }
                        },
                    )
                },
            )

            if (settings.statisticsSyncEnabled) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Statistics Sync Mode",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box {
                            TextButton(onClick = { statsModeMenuExpanded = true }) {
                                Text(settings.statisticsSyncMode.rawValue)
                            }
                            DropdownMenu(
                                expanded = statsModeMenuExpanded,
                                onDismissRequest = { statsModeMenuExpanded = false },
                            ) {
                                StatisticsSyncMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.rawValue) },
                                        onClick = {
                                            statsModeMenuExpanded = false
                                            syncManager.updateSettings { s -> s.copy(statisticsSyncMode = mode) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Sync Audiobook Position") },
                trailingContent = {
                    Switch(
                        checked = settings.audioBookSyncEnabled,
                        onCheckedChange = { checked ->
                            syncManager.updateSettings { s -> s.copy(audioBookSyncEnabled = checked) }
                        },
                    )
                },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val books = com.canopus.chimareader.data.BookStorage.loadAllBooks(context)
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

        // Device Code setup card
        Spacer(Modifier.height(18.dp))
        DeviceCodeSetupCard()
    }
}

@Composable
private fun DeviceCodeSetupCard() {
    val context = LocalContext.current
    SettingsCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Device Code setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = GoogleCloudOAuthConfiguration.introduction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = GoogleCloudOAuthConfiguration.ttuSetupLinkLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(GoogleCloudOAuthConfiguration.ttuSetupUrl)),
                    )
                },
            )
            GoogleCloudOAuthConfiguration.instructions.forEachIndexed { index, instruction ->
                InstructionTextWithLinks(
                    index = index,
                    instruction = instruction,
                    links = GoogleCloudOAuthConfiguration.instructionLinks,
                )
            }
        }
    }
}

@Composable
private fun InstructionTextWithLinks(
    index: Int,
    instruction: String,
    links: Map<String, String>,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        append("${index + 1}. ")
        var cursor = 0
        while (cursor < instruction.length) {
            val nextLink = links.keys
                .mapNotNull { label ->
                    val start = instruction.indexOf(label, startIndex = cursor)
                    if (start >= 0) label to start else null
                }
                .minByOrNull { it.second }

            if (nextLink == null) {
                append(instruction.substring(cursor))
                break
            }

            val (label, start) = nextLink
            append(instruction.substring(cursor, start))
            withLink(
                LinkAnnotation.Url(
                    url = links.getValue(label),
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ) {
                append(label)
            }
            cursor = start + label.length
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private data class SyncConnectionActions(
    val showConnect: Boolean,
    val connectEnabled: Boolean,
    val showSignOut: Boolean,
    val signOutEnabled: Boolean,
)
