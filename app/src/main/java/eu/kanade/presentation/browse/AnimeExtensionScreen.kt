package eu.kanade.presentation.browse

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.browse.components.BaseBrowseItem
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.manga.components.DotSeparatorNoSpaceText
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.rememberRequestPackageInstallsPermissionState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.animeextension.AnimeExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.animeextension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.launchRequestPackageInstallsPermission
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun AnimeExtensionScreen(
    state: AnimeExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    searchQuery: String?,
    onLongClickItem: (AnimeExtension) -> Unit,
    onClickItemCancel: (AnimeExtension) -> Unit,
    onOpenWebView: (AnimeExtension.Available) -> Unit,
    onInstallExtension: (AnimeExtension.Available) -> Unit,
    onUninstallExtension: (AnimeExtension) -> Unit,
    onUpdateExtension: (AnimeExtension.Installed) -> Unit,
    onTrustExtension: (AnimeExtension.Untrusted) -> Unit,
    onOpenExtension: (AnimeExtension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
) {
    PullRefresh(
        refreshing = state.isRefreshing,
        onRefresh = onRefresh,
        enabled = !state.isLoading,
    ) {
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.isEmpty -> {
                val msg = if (!searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.empty_screen
                }
                EmptyScreen(msg, modifier = Modifier.padding(contentPadding))
            }
            else -> {
                AnimeExtensionContent(
                    state = state,
                    contentPadding = contentPadding,
                    onLongClickItem = onLongClickItem,
                    onClickItemCancel = onClickItemCancel,
                    onOpenWebView = onOpenWebView,
                    onInstallExtension = onInstallExtension,
                    onUninstallExtension = onUninstallExtension,
                    onUpdateExtension = onUpdateExtension,
                    onTrustExtension = onTrustExtension,
                    onOpenExtension = onOpenExtension,
                    onClickUpdateAll = onClickUpdateAll,
                )
            }
        }
    }
}

@Composable
private fun AnimeExtensionContent(
    state: AnimeExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    onLongClickItem: (AnimeExtension) -> Unit,
    onClickItemCancel: (AnimeExtension) -> Unit,
    onOpenWebView: (AnimeExtension.Available) -> Unit,
    onInstallExtension: (AnimeExtension.Available) -> Unit,
    onUninstallExtension: (AnimeExtension) -> Unit,
    onUpdateExtension: (AnimeExtension.Installed) -> Unit,
    onTrustExtension: (AnimeExtension.Untrusted) -> Unit,
    onOpenExtension: (AnimeExtension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
) {
    val context = LocalContext.current
    var trustState by remember { mutableStateOf<AnimeExtension.Untrusted?>(null) }
    val installGranted = rememberRequestPackageInstallsPermissionState(initialValue = true)

    FastScrollLazyColumn(
        contentPadding = contentPadding + topSmallPaddingValues,
    ) {
        if (!installGranted && state.installer?.requiresSystemPermission == true) {
            item(key = "anime-extension-permissions-warning") {
                WarningBanner(
                    textRes = MR.strings.ext_permission_install_apps_warning,
                    modifier = Modifier.clickable {
                        context.launchRequestPackageInstallsPermission()
                    },
                )
            }
        }

        state.items.forEach { (header, items) ->
            item(
                contentType = "header",
                key = "animeExtHeader-${header.hashCode()}",
            ) {
                when (header) {
                    is AnimeExtensionUiModel.Header.Resource -> {
                        val action: @Composable RowScope.() -> Unit =
                            if (header.textRes == MR.strings.ext_updates_pending) {
                                {
                                    Button(onClick = { onClickUpdateAll() }) {
                                        Text(
                                            text = stringResource(MR.strings.ext_update_all),
                                            style = LocalTextStyle.current.copy(
                                                color = MaterialTheme.colorScheme.onPrimary,
                                            ),
                                        )
                                    }
                                }
                            } else {
                                {}
                            }
                        Row(
                            modifier = Modifier
                                .padding(horizontal = MaterialTheme.padding.medium)
                                .animateItemFastScroll(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(header.textRes),
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .weight(1f),
                                style = MaterialTheme.typography.header,
                            )
                            action()
                        }
                    }
                    is AnimeExtensionUiModel.Header.Text -> {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = MaterialTheme.padding.medium)
                                .animateItemFastScroll(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = header.text,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .weight(1f),
                                style = MaterialTheme.typography.header,
                            )
                        }
                    }
                }
            }

            items(
                items = items,
                contentType = { "item" },
                key = { item ->
                    when (item.extension) {
                        is AnimeExtension.Untrusted -> "anime-ext-untrusted-${item.hashCode()}"
                        is AnimeExtension.Installed -> "anime-ext-installed-${item.hashCode()}"
                        is AnimeExtension.Available -> "anime-ext-available-${item.hashCode()}"
                    }
                },
            ) { item ->
                AnimeExtensionItem(
                    modifier = Modifier.animateItemFastScroll(),
                    item = item,
                    onClickItem = {
                        when (it) {
                            is AnimeExtension.Available -> onInstallExtension(it)
                            is AnimeExtension.Installed -> onOpenExtension(it)
                            is AnimeExtension.Untrusted -> { trustState = it }
                        }
                    },
                    onLongClickItem = onLongClickItem,
                    onClickItemCancel = onClickItemCancel,
                    onClickItemAction = {
                        when (it) {
                            is AnimeExtension.Available -> onInstallExtension(it)
                            is AnimeExtension.Installed -> {
                                if (it.hasUpdate) onUpdateExtension(it) else onOpenExtension(it)
                            }
                            is AnimeExtension.Untrusted -> { trustState = it }
                        }
                    },
                    onClickItemSecondaryAction = {
                        when (it) {
                            is AnimeExtension.Available -> onOpenWebView(it)
                            is AnimeExtension.Installed -> onOpenExtension(it)
                            else -> {}
                        }
                    },
                )
            }
        }
    }
    if (trustState != null) {
        AlertDialog(
            title = { Text(text = stringResource(MR.strings.untrusted_extension)) },
            text = { Text(text = stringResource(MR.strings.untrusted_extension_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onTrustExtension(trustState!!)
                    trustState = null
                }) { Text(text = stringResource(MR.strings.ext_trust)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onUninstallExtension(trustState!!)
                    trustState = null
                }) { Text(text = stringResource(MR.strings.ext_uninstall)) }
            },
            onDismissRequest = { trustState = null },
        )
    }
}

@Composable
private fun AnimeExtensionItem(
    item: AnimeExtensionUiModel.Item,
    onClickItem: (AnimeExtension) -> Unit,
    onLongClickItem: (AnimeExtension) -> Unit,
    onClickItemCancel: (AnimeExtension) -> Unit,
    onClickItemAction: (AnimeExtension) -> Unit,
    onClickItemSecondaryAction: (AnimeExtension) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (extension, installStep) = item
    BaseBrowseItem(
        modifier = modifier
            .combinedClickable(
                onClick = { onClickItem(extension) },
                onLongClick = { onLongClickItem(extension) },
            ),
        onClickItem = { onClickItem(extension) },
        onLongClickItem = { onLongClickItem(extension) },
        icon = {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                val idle = installStep.isCompleted()
                if (!idle) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 2.dp,
                    )
                }

                val padding by animateDpAsState(targetValue = if (idle) 0.dp else 8.dp)
                when (extension) {
                    is AnimeExtension.Available -> {
                        AsyncImage(
                            model = extension.iconUrl,
                            contentDescription = null,
                            placeholder = ColorPainter(Color(0x1F888888)),
                            modifier = Modifier
                                .matchParentSize()
                                .padding(padding),
                        )
                    }
                    is AnimeExtension.Installed -> {
                        AsyncImage(
                            model = extension.icon,
                            contentDescription = null,
                            placeholder = ColorPainter(Color(0x1F888888)),
                            modifier = Modifier
                                .matchParentSize()
                                .padding(padding),
                        )
                    }
                    is AnimeExtension.Untrusted -> {
                        AsyncImage(
                            model = R.drawable.cover_error,
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize()
                                .padding(padding),
                        )
                    }
                }
            }
        },
        action = {
            AnimeExtensionItemActions(
                extension = extension,
                installStep = installStep,
                onClickItemCancel = onClickItemCancel,
                onClickItemAction = onClickItemAction,
                onClickItemSecondaryAction = onClickItemSecondaryAction,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium),
        ) {
            Text(
                text = extension.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )

            FlowRow(
                modifier = Modifier.secondaryItemAlpha(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                    extension.lang?.let {
                        if (it.isNotEmpty()) {
                            Text(text = LocaleHelper.getSourceDisplayName(it, LocalContext.current))
                        }
                    }

                    if (extension.versionName.isNotEmpty()) {
                        DotSeparatorNoSpaceText()
                        Text(text = extension.versionName)
                    }

                    val warning = when {
                        extension is AnimeExtension.Untrusted -> MR.strings.ext_untrusted
                        extension is AnimeExtension.Installed && extension.isObsolete -> MR.strings.ext_obsolete
                        extension.isNsfw -> MR.strings.ext_nsfw_short
                        else -> null
                    }
                    if (warning != null) {
                        DotSeparatorNoSpaceText()
                        Text(
                            text = stringResource(warning).uppercase(),
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (!installStep.isCompleted()) {
                        DotSeparatorNoSpaceText()
                        Text(
                            text = when (installStep) {
                                InstallStep.Pending -> stringResource(MR.strings.ext_pending)
                                InstallStep.Downloading -> stringResource(MR.strings.ext_downloading)
                                InstallStep.Installing -> stringResource(MR.strings.ext_installing)
                                else -> error("Must not show non-install process text")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeExtensionItemActions(
    extension: AnimeExtension,
    installStep: InstallStep,
    onClickItemCancel: (AnimeExtension) -> Unit,
    onClickItemAction: (AnimeExtension) -> Unit,
    onClickItemSecondaryAction: (AnimeExtension) -> Unit,
) {
    val isIdle = installStep.isCompleted()

    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
        when {
            !isIdle -> {
                IconButton(onClick = { onClickItemCancel(extension) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(MR.strings.action_cancel))
                }
            }
            installStep == InstallStep.Error -> {
                IconButton(onClick = { onClickItemAction(extension) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(MR.strings.action_retry))
                }
            }
            installStep == InstallStep.Idle -> {
                when (extension) {
                    is AnimeExtension.Installed -> {
                        IconButton(onClick = { onClickItemSecondaryAction(extension) }) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.action_settings))
                        }
                        if (extension.hasUpdate) {
                            IconButton(onClick = { onClickItemAction(extension) }) {
                                Icon(Icons.Outlined.GetApp, contentDescription = stringResource(MR.strings.ext_update))
                            }
                        }
                    }
                    is AnimeExtension.Untrusted -> {
                        IconButton(onClick = { onClickItemAction(extension) }) {
                            Icon(Icons.Outlined.VerifiedUser, contentDescription = stringResource(MR.strings.ext_trust))
                        }
                    }
                    is AnimeExtension.Available -> {
                        if (extension.sources.isNotEmpty()) {
                            IconButton(onClick = { onClickItemSecondaryAction(extension) }) {
                                Icon(Icons.Outlined.Public, contentDescription = stringResource(MR.strings.action_open_in_web_view))
                            }
                        }
                        IconButton(onClick = { onClickItemAction(extension) }) {
                            Icon(Icons.Outlined.GetApp, contentDescription = stringResource(MR.strings.ext_install))
                        }
                    }
                }
            }
        }
    }
}
