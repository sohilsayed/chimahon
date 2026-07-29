package mihon.core.migration.migrations

import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext

class MoveJimakuApiKeyToPrivateMigration : Migration {
    override val version: Float = 82f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return@withIOContext false

        migrate(preferenceStore)

        return@withIOContext true
    }

    internal fun migrate(preferenceStore: PreferenceStore) {
        MigrateUtils.replacePreferences(
            preferenceStore = preferenceStore,
            filterPredicate = { it.key == JIMAKU_API_KEY },
            newKey = { Preference.privateKey(it) },
        )
    }

    private companion object {
        const val JIMAKU_API_KEY = "pref_jimaku_api_key"
    }
}
