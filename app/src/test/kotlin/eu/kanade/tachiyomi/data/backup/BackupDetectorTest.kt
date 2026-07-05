package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test

class BackupDetectorTest {

    @Test
    fun `detects Anikku legacy anime backup`() {
        val bytes = ProtoBuf.encodeToByteArray(
            LegacyBackup.serializer(),
            LegacyBackup(
                backupAnimeSources = listOf(BackupAnimeSource("Anime source", 123L)),
            ),
        )

        BackupDetector.isLegacyBackup(bytes).shouldBeTrue()
    }

    @Test
    fun `does not classify current anime backup as legacy`() {
        val bytes = ProtoBuf.encodeToByteArray(
            CurrentAnikkuBackup.serializer(),
            CurrentAnikkuBackup(
                isLegacy = false,
                backupAnimeSources = listOf(BackupAnimeSource("Anime source", 123L)),
            ),
        )

        BackupDetector.isLegacyBackup(bytes).shouldBeFalse()
    }

    @Test
    fun `does not classify current manga backup as legacy`() {
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), Backup())

        BackupDetector.isLegacyBackup(bytes).shouldBeFalse()
    }

    @Test
    fun `maps Anikku legacy anime fields to current backup fields`() {
        val legacy = LegacyBackup(
            backupAnimeSources = listOf(BackupAnimeSource("Anime source", 123L)),
        )

        legacy.toBackup().backupAnimeSources.shouldContainExactly(
            BackupAnimeSource("Anime source", 123L),
        )
    }

    @Serializable
    private data class CurrentAnikkuBackup(
        @ProtoNumber(500) val isLegacy: Boolean = false,
        @ProtoNumber(503) val backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    )
}
