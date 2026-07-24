package tachiyomi.domain

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.service.AppUpdatePolicy

class UnsortedPreferences(
    private val preferenceStore: PreferenceStore,
) {
    // KMK -->
    fun appShouldAutoUpdate() = preferenceStore.getStringSet(
        "should_auto_update",
        setOf(
            AppUpdatePolicy.DEVICE_ONLY_ON_WIFI,
        ),
    )
    // KMK <--
}
