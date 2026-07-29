package mihon.core.migration.migrations

import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class MoveJimakuApiKeyToPrivateMigrationTest {

    @Test
    fun movesExistingApiKeyToPrivatePreference() {
        val preferenceStore = mockk<PreferenceStore>()
        val legacyPreference = mockk<Preference<String>>(relaxed = true)
        val privatePreference = mockk<Preference<String>>(relaxed = true)
        every { preferenceStore.getAll() } returns mapOf(JIMAKU_API_KEY to API_KEY)
        every { preferenceStore.getString(JIMAKU_API_KEY, "") } returns legacyPreference
        every { preferenceStore.getString(PRIVATE_JIMAKU_API_KEY, "") } returns privatePreference

        MoveJimakuApiKeyToPrivateMigration().migrate(preferenceStore)

        verifyOrder {
            preferenceStore.getAll()
            preferenceStore.getString(PRIVATE_JIMAKU_API_KEY, "")
            privatePreference.set(API_KEY)
            preferenceStore.getString(JIMAKU_API_KEY, "")
            legacyPreference.delete()
        }
    }

    private companion object {
        const val JIMAKU_API_KEY = "pref_jimaku_api_key"
        const val API_KEY = "existing-api-key"
        val PRIVATE_JIMAKU_API_KEY = Preference.privateKey(JIMAKU_API_KEY)
    }
}
