package chimahon.anki

import android.content.ContextWrapper
import chimahon.LookupResult
import chimahon.TermResult
import chimahon.TransformGroup
import com.canopus.chimareader.data.AnkiStatsStorage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class AnkiCardCreatorSentenceAudioTest {

    @AfterEach
    fun tearDown() {
        AnkiCardCreator.resetBridgeFactoryForTests()
        AnkiCardCreator.resetFieldMapParserForTests()
    }

    @Test
    fun `legacy sentence bytes use the typed source and commit to a note`() = runTest {
        val bridge = FakeBridge(listOf(emptyList()))
        AnkiCardCreator.bridgeFactory = { bridge }
        AnkiCardCreator.fieldMapParser = { mapOf("Audio" to "{sentence-audio}", "Image" to "{screenshot}") }
        val bytes = byteArrayOf(1, 2, 3)

        val result = addCard(sentenceAudioBytes = bytes)

        assertEquals(AnkiResult.Success(7), result)
        assertEquals(1, bridge.storedSentenceAudio.size)
        assertEquals(true, bridge.storedSentenceAudio.single().data.contentEquals(bytes))
        assertEquals("m4a", bridge.storedSentenceAudio.single().extension)
        assertEquals(1, bridge.addNoteCalls)
    }

    @Test
    fun `legacy screenshot bytes still store even without a screenshot marker`() = runTest {
        val bridge = FakeBridge(listOf(emptyList()))
        AnkiCardCreator.bridgeFactory = { bridge }
        AnkiCardCreator.fieldMapParser = { mapOf("Audio" to "{sentence-audio}") }

        val result = addCard(screenshotBytes = byteArrayOf(7))

        assertEquals(AnkiResult.Success(7), result)
        assertEquals(1, bridge.storedBytes.size)
        assertEquals(1, bridge.addNoteCalls)
    }

    @Test
    fun `final duplicate recheck returns card exists before any prepared media store`() = runTest {
        val bridge = FakeBridge(listOf(emptyList(), listOf(42L)))
        AnkiCardCreator.bridgeFactory = { bridge }
        AnkiCardCreator.fieldMapParser = { mapOf("Audio" to "{sentence-audio}", "Image" to "{screenshot}") }

        val result = addCard(
            screenshotBytes = byteArrayOf(5),
            sentenceAudioBytes = byteArrayOf(6),
            dupCheck = true,
            dupAction = "prevent",
        )

        assertEquals(AnkiResult.CardExists(42), result)
        assertEquals(emptyList<AnkiSentenceAudioSource>(), bridge.storedSentenceAudio)
        assertEquals(emptyList<Pair<String, ByteArray>>(), bridge.storedBytes)
        assertEquals(0, bridge.addNoteCalls)
    }

    @Test
    fun `final duplicate recheck opens the existing card before any prepared media store`() = runTest {
        val bridge = FakeBridge(listOf(emptyList(), listOf(43L)))
        AnkiCardCreator.bridgeFactory = { bridge }
        AnkiCardCreator.fieldMapParser = { mapOf("Audio" to "{sentence-audio}", "Image" to "{screenshot}") }

        val result = addCard(
            screenshotBytes = byteArrayOf(5),
            sentenceAudioBytes = byteArrayOf(6),
            dupCheck = true,
            dupAction = "open",
        )

        assertEquals(AnkiResult.OpenCard(43), result)
        assertEquals(emptyList<AnkiSentenceAudioSource>(), bridge.storedSentenceAudio)
        assertEquals(emptyList<Pair<String, ByteArray>>(), bridge.storedBytes)
        assertEquals(0, bridge.addNoteCalls)
    }

    @Test
    fun `duplicate lookup returns the first Anki note ID for each existing expression`() = runTest {
        val bridge = FakeBridge(listOf(listOf(41L, 42L), emptyList(), listOf(73L)))
        AnkiCardCreator.bridgeFactory = { bridge }

        val existing = AnkiCardCreator.checkExistingCardIds(
            context = TestContext,
            expressions = listOf("existing", "missing", "existing", "another"),
        )

        assertEquals(mapOf("existing" to 41L, "another" to 73L), existing)
    }

    @Test
    fun `final duplicate recheck overwrites only after storing prepared media`() = runTest {
        val bridge = FakeBridge(listOf(emptyList(), listOf(44L)))
        AnkiCardCreator.bridgeFactory = { bridge }
        AnkiCardCreator.fieldMapParser = { mapOf("Audio" to "{sentence-audio}", "Image" to "{screenshot}") }

        val result = addCard(
            screenshotBytes = byteArrayOf(5),
            sentenceAudioBytes = byteArrayOf(6),
            dupCheck = true,
            dupAction = "overwrite",
        )

        assertEquals(AnkiResult.Success(44), result)
        assertEquals(1, bridge.storedSentenceAudio.size)
        assertEquals(1, bridge.storedBytes.size)
        assertEquals(0, bridge.addNoteCalls)
        assertEquals(1, bridge.updateNoteCalls)
    }

    private suspend fun addCard(
        screenshotBytes: ByteArray? = null,
        sentenceAudioBytes: ByteArray? = null,
        dupCheck: Boolean = false,
        dupAction: String = "prevent",
    ): AnkiResult = AnkiCardCreator.addToAnki(
        context = TestContext,
        result = lookupResult(),
        deck = "deck",
        model = "model",
        fieldMapJson = """{"Front":"{expression}","Audio":"{sentence-audio}","Image":"{screenshot}"}""",
        tags = "",
        dupCheck = dupCheck,
        dupScope = "collection",
        dupAction = dupAction,
        screenshotBytes = screenshotBytes,
        sentenceAudioBytes = sentenceAudioBytes,
    )

    private fun lookupResult(): LookupResult = LookupResult(
        matched = "word",
        deinflected = "word",
        process = emptyArray<TransformGroup>(),
        term = TermResult(
            expression = "word",
            reading = "word",
            rules = "",
            glossaries = emptyArray(),
            frequencies = emptyArray(),
            pitches = emptyArray(),
        ),
        preprocessorSteps = 0,
    )

    private class FakeBridge(
        private val noteLookups: List<List<Long>>,
    ) : AnkiCardBridge {
        private var lookupIndex = 0
        val storedSentenceAudio = mutableListOf<AnkiSentenceAudioSource>()
        val storedBytes = mutableListOf<Pair<String, ByteArray>>()
        var addNoteCalls = 0
        var updateNoteCalls = 0

        override fun hasPermission(): Boolean = true
        override suspend fun ensureDefaultDeckName(): String = "deck"
        override suspend fun ensureLapisModelName(): String = "model"
        override suspend fun getDeckId(deckName: String): Long = 1L
        override suspend fun findNotes(expression: String, modelName: String?, deckId: Long?): List<Long> =
            noteLookups.getOrElse(lookupIndex++) { emptyList() }
        override suspend fun storeMedia(filename: String, data: ByteArray): String {
            storedBytes += filename to data
            return filename
        }
        override suspend fun storeMedia(source: AnkiSentenceAudioSource): String {
            storedSentenceAudio += source
            return "${source.preferredBaseName}.${source.extension}"
        }
        override suspend fun addNote(deckName: String, modelName: String, fields: Map<String, String>, tags: List<String>): Long {
            addNoteCalls++
            return 7L
        }
        override suspend fun updateNoteFields(noteId: Long, fields: Map<String, String>) {
            updateNoteCalls++
        }
        override fun triggerSync() = Unit
    }

    private object TestContext : ContextWrapper(null) {
        private val files = Files.createTempDirectory("anki-card-creator-test").toFile()
        override fun getFilesDir(): File = files
    }
}
