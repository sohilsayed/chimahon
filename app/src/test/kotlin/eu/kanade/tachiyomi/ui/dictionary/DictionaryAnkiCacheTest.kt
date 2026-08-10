package eu.kanade.tachiyomi.ui.dictionary

import chimahon.anki.AnkiResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DictionaryAnkiCacheTest {

    @Test
    fun `caches note IDs returned by every successful Anki outcome`() {
        cacheAnkiResultNoteId(emptyMap(), "見る", AnkiResult.Success(11)) shouldBe mapOf("見る" to 11L)
        cacheAnkiResultNoteId(emptyMap(), "聞く", AnkiResult.CardExists(12)) shouldBe mapOf("聞く" to 12L)
        cacheAnkiResultNoteId(emptyMap(), "読む", AnkiResult.OpenCard(13)) shouldBe mapOf("読む" to 13L)
    }
}
