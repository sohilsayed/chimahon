package chimahon.anki

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface AnkiDuplicateDecision {
    data object Insert : AnkiDuplicateDecision
    data class Overwrite(val noteId: Long) : AnkiDuplicateDecision
    data class Return(val result: AnkiResult) : AnkiDuplicateDecision
}

/**
 * A fixed stripe set avoids both a growing lock map and the race from discarding a key lock
 * between preflight and final media/note commit.
 */
internal class AnkiDuplicateGate {
    private val stripes = List(16) { Mutex() }

    private fun stripeFor(key: String): Mutex =
        stripes[key.hashCode().ushr(1) % stripes.size]

    suspend fun preflight(
        key: String,
        decision: suspend () -> AnkiDuplicateDecision,
    ): AnkiDuplicateDecision = stripeFor(key).withLock { decision() }

    suspend fun <T> commit(
        key: String,
        decision: suspend () -> AnkiDuplicateDecision,
        block: suspend (AnkiDuplicateDecision) -> T,
    ): T = stripeFor(key).withLock {
        block(decision())
    }
}
