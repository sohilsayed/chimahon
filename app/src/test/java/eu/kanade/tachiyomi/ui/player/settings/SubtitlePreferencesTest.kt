package eu.kanade.tachiyomi.ui.player.settings

import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class SubtitlePreferencesTest {

    @Test
    fun jimakuApiKeyUsesAPrivatePreference() {
        val preferenceStore = mockk<PreferenceStore>()
        val apiKeyPreference = mockk<Preference<String>>()
        every { preferenceStore.getString(PRIVATE_JIMAKU_API_KEY, "") } returns apiKeyPreference

        val preference = SubtitlePreferences(preferenceStore).jimakuApiKey()

        assertSame(apiKeyPreference, preference)
        verify { preferenceStore.getString(PRIVATE_JIMAKU_API_KEY, "") }
    }

    @Test
    fun jimakuApiKeyIsExcludedFromNormalAppBackups() {
        val preferenceStore = mockk<PreferenceStore>()
        every { preferenceStore.getAll() } returns mapOf(PRIVATE_JIMAKU_API_KEY to "api-key")
        val backupCreator = PreferenceBackupCreator(
            sourceManager = mockk(),
            preferenceStore = preferenceStore,
        )

        assertEquals(emptyList<String>(), backupCreator.createApp(includePrivatePreferences = false).map { it.key })
        assertEquals(
            listOf(PRIVATE_JIMAKU_API_KEY),
            backupCreator.createApp(includePrivatePreferences = true).map { it.key },
        )
    }

    private companion object {
        const val JIMAKU_API_KEY = "pref_jimaku_api_key"
        val PRIVATE_JIMAKU_API_KEY = Preference.privateKey(JIMAKU_API_KEY)
    }
}
