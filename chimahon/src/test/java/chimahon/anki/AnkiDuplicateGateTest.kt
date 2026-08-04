package chimahon.anki

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AnkiDuplicateGateTest {

    @Test
    fun `same key final commits serialize through the fixed stripe after separate preflights`() = runTest {
        val gate = AnkiDuplicateGate()
        val enteredFirst = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var enteredSecond = false

        assertEquals(AnkiDuplicateDecision.Insert, gate.preflight("term") { AnkiDuplicateDecision.Insert })
        assertEquals(AnkiDuplicateDecision.Insert, gate.preflight("term") { AnkiDuplicateDecision.Insert })

        val first = async {
            gate.commit("term", { AnkiDuplicateDecision.Insert }) {
                enteredFirst.complete(Unit)
                releaseFirst.await()
            }
        }
        enteredFirst.await()
        val second = async {
            gate.commit("term", { AnkiDuplicateDecision.Insert }) {
                enteredSecond = true
            }
        }
        advanceUntilIdle()
        assertFalse(enteredSecond)

        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertEquals(true, enteredSecond)
    }
}
