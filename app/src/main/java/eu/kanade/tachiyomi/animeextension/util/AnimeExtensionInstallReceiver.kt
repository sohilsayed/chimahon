package eu.kanade.tachiyomi.animeextension.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import eu.kanade.tachiyomi.animeextension.model.AnimeLoadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

internal class AnimeExtensionInstallReceiver(private val listener: Listener) : BroadcastReceiver() {

    val scope = CoroutineScope(SupervisorJob())

    fun register(context: Context) {
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private val filter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(ACTION_ANIME_EXTENSION_ADDED)
        addAction(ACTION_ANIME_EXTENSION_REPLACED)
        addAction(ACTION_ANIME_EXTENSION_REMOVED)
        addDataScheme("package")
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED, ACTION_ANIME_EXTENSION_ADDED -> {
                if (isReplacing(intent)) return

                scope.launch {
                    when (val result = getExtensionFromIntent(context, intent)) {
                        is AnimeLoadResult.Success -> listener.onExtensionInstalled(result.extension)
                        is AnimeLoadResult.Untrusted -> listener.onExtensionUntrusted(result.extension)
                        else -> {}
                    }
                }
            }
            Intent.ACTION_PACKAGE_REPLACED, ACTION_ANIME_EXTENSION_REPLACED -> {
                scope.launch {
                    when (val result = getExtensionFromIntent(context, intent)) {
                        is AnimeLoadResult.Success -> listener.onExtensionUpdated(result.extension)
                        is AnimeLoadResult.Untrusted -> listener.onExtensionUntrusted(result.extension)
                        else -> {}
                    }
                }
            }
            Intent.ACTION_PACKAGE_REMOVED, ACTION_ANIME_EXTENSION_REMOVED -> {
                if (isReplacing(intent)) return

                val pkgName = getPackageNameFromIntent(intent)
                if (pkgName != null) {
                    listener.onPackageUninstalled(pkgName)
                }
            }
        }
    }

    private fun isReplacing(intent: Intent): Boolean {
        return intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
    }

    private suspend fun getExtensionFromIntent(context: Context, intent: Intent?): AnimeLoadResult {
        val pkgName = getPackageNameFromIntent(intent)
        if (pkgName == null) {
            logcat(LogPriority.WARN) { "Anime extension package name not found" }
            return AnimeLoadResult.Error
        }
        return AnimeExtensionLoader.loadExtensionFromPkgName(context, pkgName)
    }

    private fun getPackageNameFromIntent(intent: Intent?): String? {
        return intent?.data?.encodedSchemeSpecificPart ?: return null
    }

    interface Listener {
        fun onExtensionInstalled(extension: AnimeExtension.Installed)
        fun onExtensionUpdated(extension: AnimeExtension.Installed)
        fun onExtensionUntrusted(extension: AnimeExtension.Untrusted)
        fun onPackageUninstalled(pkgName: String)
    }

    companion object {
        private const val ACTION_ANIME_EXTENSION_ADDED = "${BuildConfig.APPLICATION_ID}.ACTION_ANIME_EXTENSION_ADDED"
        private const val ACTION_ANIME_EXTENSION_REPLACED = "${BuildConfig.APPLICATION_ID}.ACTION_ANIME_EXTENSION_REPLACED"
        private const val ACTION_ANIME_EXTENSION_REMOVED = "${BuildConfig.APPLICATION_ID}.ACTION_ANIME_EXTENSION_REMOVED"

        fun notifyAdded(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_ANIME_EXTENSION_ADDED)
        }

        fun notifyReplaced(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_ANIME_EXTENSION_REPLACED)
        }

        fun notifyRemoved(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_ANIME_EXTENSION_REMOVED)
        }

        private fun notify(context: Context, pkgName: String, action: String) {
            Intent(action).apply {
                data = "package:$pkgName".toUri()
                `package` = context.packageName
                context.sendBroadcast(this)
            }
        }
    }
}
