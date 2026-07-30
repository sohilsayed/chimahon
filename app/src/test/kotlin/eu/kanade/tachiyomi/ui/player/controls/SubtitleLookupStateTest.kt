package eu.kanade.tachiyomi.ui.player.controls

import chimahon.anki.AnkiProfile
import chimahon.anki.AnkiProfileStore
import chimahon.dictionary.DictionaryProfileResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubtitleLookupStateTest {

    @Test
    fun `a different subtitle tap replaces an in progress capture without pausing again`() {
        val transition = subtitleLookupTapTransition(
            matchesCurrentTap = false,
            hasOpenLookup = true,
            hasActiveCapture = true,
        )

        assertEquals(SubtitleLookupTapAction.Replace, transition.action)
        assertTrue(transition.cancelActiveCapture)
        assertFalse(transition.pausePlayer)
        assertFalse(
            subtitleLookupPauseStateAfterTap(
                wasPlayerAlreadyPaused = false,
                playerWasPausedAtTap = true,
                pausePlayer = transition.pausePlayer,
            ),
        )
    }

    @Test
    fun `a repeated subtitle tap dismisses the current lookup`() {
        val transition = subtitleLookupTapTransition(
            matchesCurrentTap = true,
            hasOpenLookup = true,
            hasActiveCapture = true,
        )

        assertEquals(SubtitleLookupTapAction.Dismiss, transition.action)
    }

    @Test
    fun `profile resolution key changes with the anime id`() {
        val firstAnime = DictionaryProfileResolutionKey(
            animeId = 1L,
            sourceId = 2L,
            sourceLang = "ja",
        )
        val secondAnime = firstAnime.copy(animeId = 3L)

        assertNotEquals(firstAnime, secondAnime)
    }

    @Test
    fun `a late capture result is released after a newer lookup replaces it`() {
        assertEquals(
            SubtitleLookupCaptureResultAction.Release,
            subtitleLookupCaptureResultAction(
                captureIsActive = true,
                captureGeneration = 1,
                currentGeneration = 2,
                hasOpenLookup = true,
            ),
        )
    }

    @Test
    fun `profile resolution applies the current anime override`() {
        var profilesJson = "[]"
        var activeProfileId = ""
        val defaultProfile = AnkiProfile(id = "default", name = "Default", languageCode = "ja")
        val animeProfile = AnkiProfile(id = "anime", name = "Anime", languageCode = "en")
        val store = AnkiProfileStore(
            readProfiles = { profilesJson },
            writeProfiles = { profilesJson = it },
            readActiveId = { activeProfileId },
            writeActiveId = { activeProfileId = it },
        )
        store.saveProfiles(listOf(defaultProfile, animeProfile))
        store.setActiveProfile(defaultProfile.id)
        val resolver = DictionaryProfileResolver(
            profileStore = store,
            readMangaOverride = { mangaId -> if (mangaId == 42L) animeProfile.id else "" },
            readSourceOverride = { "" },
        )

        val resolved = resolver.resolveForPlayer(
            DictionaryProfileResolutionKey(
                animeId = 42L,
                sourceId = 7L,
                sourceLang = "ja",
            ),
        )

        assertEquals(animeProfile, resolved)
    }
}
