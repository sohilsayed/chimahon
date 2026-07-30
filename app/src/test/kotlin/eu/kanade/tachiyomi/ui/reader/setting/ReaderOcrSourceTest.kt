package eu.kanade.tachiyomi.ui.reader.setting

import eu.kanade.tachiyomi.data.ocr.OcrEngineType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

class ReaderOcrSourceTest {

    @Test
    fun `automatic preserves the existing OCR pipeline`() {
        val source = ReaderOcrSource.AUTOMATIC

        assertTrue(source.usesMokuro)
        assertTrue(source.usesPersistentCache)
        assertNull(source.recognitionEngine)
    }

    @Test
    fun `explicit sources have strict policies`() {
        assertTrue(ReaderOcrSource.MOKURO.usesMokuro)
        assertFalse(ReaderOcrSource.MOKURO.usesPersistentCache)
        assertNull(ReaderOcrSource.MOKURO.recognitionEngine)

        assertFalse(ReaderOcrSource.GOOGLE_LENS.usesMokuro)
        assertFalse(ReaderOcrSource.GOOGLE_LENS.usesPersistentCache)
        assertEquals(OcrEngineType.CLOUD, ReaderOcrSource.GOOGLE_LENS.recognitionEngine)

        assertFalse(ReaderOcrSource.LOCAL.usesMokuro)
        assertFalse(ReaderOcrSource.LOCAL.usesPersistentCache)
        assertEquals(OcrEngineType.LOCAL, ReaderOcrSource.LOCAL.recognitionEngine)
    }

    @Test
    fun `local source is only offered when available`() {
        assertEquals(ReaderOcrSource.entries, ReaderOcrSource.availableSources(localOcrAvailable = true, mokuroAvailable = true))
        assertFalse(ReaderOcrSource.LOCAL in ReaderOcrSource.availableSources(localOcrAvailable = false, mokuroAvailable = true))
    }

    @Test
    fun `preference key is app state scoped per manga`() {
        val firstMangaKey = readerOcrSourcePreferenceKey(1)
        val secondMangaKey = readerOcrSourcePreferenceKey(2)

        assertNotEquals(firstMangaKey, secondMangaKey)
        assertTrue(Preference.isAppState(firstMangaKey))
        assertTrue(Preference.isAppState(secondMangaKey))
    }
}
