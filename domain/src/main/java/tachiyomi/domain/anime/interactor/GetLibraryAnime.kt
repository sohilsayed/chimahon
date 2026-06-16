package tachiyomi.domain.anime.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retry
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.library.model.LibraryAnime
import kotlin.time.Duration.Companion.seconds

class GetLibraryAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): List<LibraryAnime> {
        return animeRepository.getLibraryAnime()
    }

    fun subscribe(): Flow<List<LibraryAnime>> {
        return animeRepository.getLibraryAnimeAsFlow()
            // SY -->
            .retry {
                if (it is NullPointerException) {
                    delay(5.seconds)
                    true
                } else {
                    false
                }
            }
        // SY <--
    }
}
