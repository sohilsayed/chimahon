package tachiyomi.domain.source.anime.model

data class AnimeSourceWithIds(
    val source: AnimeSource,
    val ids: List<Long>,
    val orphaned: List<Long>,
) {
    val id: Long = source.id
    val name: String = source.name
}
