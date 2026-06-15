package eu.kanade.tachiyomi.ui.stats

import android.app.Application
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.Statistics
import chimahon.anki.AnkiProfile
import eu.kanade.core.util.fastCountNot
import eu.kanade.presentation.more.stats.StatsDateScale
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.presentation.more.stats.data.StatsType
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import tachiyomi.domain.source.service.SourceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.domain.history.interactor.GetAllHistory
import tachiyomi.domain.history.interactor.GetTotalReadDuration
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.ReadingSession
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetReadMangaNotInLibraryView
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

class StatsScreenModel(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getTotalReadDuration: GetTotalReadDuration = Injekt.get(),
    private val getAllHistory: GetAllHistory = Injekt.get(),
    private val historyRepository: HistoryRepository = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val preferences: LibraryPreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getReadMangaNotInLibraryView: GetReadMangaNotInLibraryView = Injekt.get(),
    private val context: Application = Injekt.get(),
    private val dictionaryPreferences: DictionaryPreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<StatsScreenState>(StatsScreenState.Loading) {

    private val loggedInTrackers by lazy { trackerManager.loggedInTrackers() }

    private val _allRead = MutableStateFlow(false)
    val allRead = _allRead.asStateFlow()

    private val _dateScale = MutableStateFlow(StatsDateScale.Day)
    val dateScale = _dateScale.asStateFlow()

    private val _dateOffset = MutableStateFlow(0)
    val dateOffset = _dateOffset.asStateFlow()

    private val _statsType = MutableStateFlow(StatsType.All)
    val statsType = _statsType.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId = _activeProfileId.asStateFlow()

    private val _profiles = MutableStateFlow<List<AnkiProfile>>(emptyList())
    val profiles = _profiles.asStateFlow()

    fun setProfileFilter(profileId: String?) {
        _activeProfileId.value = profileId
    }

    private data class CombinedFilters(
        val allRead: Boolean,
        val dateScale: StatsDateScale,
        val dateOffset: Int,
        val statsType: StatsType,
        val activeProfileId: String?,
    )

    init {
        val combinedFlow = combine(
            _allRead,
            _dateScale,
            _dateOffset,
            _statsType,
            _activeProfileId
        ) { allRead, dateScale, dateOffset, statsType, activeProfileId ->
            CombinedFilters(allRead, dateScale, dateOffset, statsType, activeProfileId)
        }

        combine(
            combinedFlow,
            dictionaryPreferences.rawProfiles().changes()
        ) { tuple, rawProfilesJson ->
            val allRead = tuple.allRead
            val dateScale = tuple.dateScale
            val dateOffset = tuple.dateOffset
            val statsType = tuple.statsType
            val activeProfileId = tuple.activeProfileId

            val libraryManga = getLibraryManga.await() + if (allRead) {
                getReadMangaNotInLibraryView.await()
            } else {
                emptyList()
            }

            val distinctLibraryManga = libraryManga.fastDistinctBy { it.id }
            
            val filteredLibraryManga = when (statsType) {
                StatsType.All -> distinctLibraryManga
                StatsType.Manga -> distinctLibraryManga
                StatsType.Novels -> emptyList()
            }

            val profilesList = dictionaryPreferences.profileStore.getProfiles()
            _profiles.value = profilesList

            // Build dynamic resolver map for manga
            val allMangaStats = com.canopus.chimareader.data.MangaStatsStorage.loadAll(context)
            val uniqueMangaIds = allMangaStats.map { it.mangaId }.distinct()
            val resolver = dictionaryPreferences.profileResolver
            val mangaProfileMap = uniqueMangaIds.associateWith { mangaId ->
                if (mangaId == 0L) {
                    ""
                } else {
                    val dbManga = distinctLibraryManga.find { it.id == mangaId }?.manga
                    val source = dbManga?.source?.let { sourceManager.getOrStub(it) }
                    resolver.resolve(
                        mangaId = mangaId,
                        sourceId = dbManga?.source ?: 0L,
                        sourceLang = source?.lang ?: "",
                    ).id
                }
            }

            // Filter manga stats by profile
            val profileFilteredMangaStats = if (activeProfileId != null) {
                allMangaStats.filter { mangaProfileMap[it.mangaId] == activeProfileId }
            } else {
                allMangaStats
            }

            val libraryMangaIds = distinctLibraryManga.map { it.id }.toSet()
            val libraryFilteredMangaStats = if (allRead) profileFilteredMangaStats else profileFilteredMangaStats.filter { it.mangaId in libraryMangaIds || it.mangaId == 0L }
            val filteredMangaStats = filterMangaStatsByScale(libraryFilteredMangaStats, dateScale, dateOffset)

            // Load and filter novels
            val allNovels = if (statsType == StatsType.All || statsType == StatsType.Novels) {
                BookStorage.loadAllBooks(context)
            } else {
                emptyList()
            }
            
            // Build dynamic resolver map for novels
            val novelProfileMap = allNovels.associate { novel ->
                novel.id to resolver.resolve(novelId = novel.id).id
            }

            val novelStats = allNovels.associate { novel ->
                val bookDir = BookStorage.getBookDirectory(context, novel.id)
                novel.id to (BookStorage.loadStatistics(bookDir) ?: emptyList())
            }

            // Filter novel stats by profile
            val profileFilteredNovelStatsMap = if (activeProfileId != null) {
                novelStats.filter { (novelId, _) -> novelProfileMap[novelId] == activeProfileId }
            } else {
                novelStats
            }
            
            val allNovelStatsList = profileFilteredNovelStatsMap.values.flatten()
            val filteredNovelStats = filterNovelStatsByScale(allNovelStatsList, dateScale, dateOffset)

            val mangaReadDuration = filteredMangaStats.sumOf { it.readingTime }
            val novelReadDurationSeconds = filteredNovelStats.sumOf { it.readingTime }
            val novelReadDurationMs = (novelReadDurationSeconds * 1000).toLong()
            
            val totalReadDuration = when (statsType) {
                StatsType.All -> mangaReadDuration + novelReadDurationMs
                StatsType.Manga -> mangaReadDuration
                StatsType.Novels -> novelReadDurationMs
            }

            val mangaChars = filteredMangaStats.sumOf { it.charactersRead }
            val mangaTimeMs = filteredMangaStats.sumOf { it.readingTime }
            
            val novelChars = filteredNovelStats.sumOf { it.charactersRead }
            val novelTimeMs = novelReadDurationMs

            val totalChars = when (statsType) {
                StatsType.All -> mangaChars + novelChars
                StatsType.Manga -> mangaChars
                StatsType.Novels -> novelChars
            }

            val totalTimeMs = when (statsType) {
                StatsType.All -> mangaTimeMs + novelTimeMs
                StatsType.Manga -> mangaTimeMs
                StatsType.Novels -> novelTimeMs
            }

            val charactersPerHour = if (totalTimeMs > 0) {
                (totalChars.toDouble() / (totalTimeMs / 3600000.0)).toInt()
            } else null

            val streak = calculateStreak(libraryFilteredMangaStats, allNovelStatsList)
            val historyPoints = calculateHistoryPoints(libraryFilteredMangaStats, allNovelStatsList, dateScale, dateOffset)
            
            // Calculate avg per day
            val avgDurationPerDay = if (dateScale != StatsDateScale.Day && dateScale != StatsDateScale.AllTime) {
                val (start, end) = getDateRange(dateScale, dateOffset)
                val days = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1) + 1
                totalReadDuration / days
            } else null

            val ankiStats = com.canopus.chimareader.data.AnkiStatsStorage.loadAll(context)
            val filteredAnkiStats = filterAnkiStatsByScale(ankiStats, dateScale, dateOffset)
            val totalAnkiCards = when (statsType) {
                StatsType.All -> filteredAnkiStats.sumOf { it.mangaCards + it.novelCards }
                StatsType.Manga -> filteredAnkiStats.sumOf { it.mangaCards }
                StatsType.Novels -> filteredAnkiStats.sumOf { it.novelCards }
            }

            val overviewStatData = StatsData.Overview(
                libraryMangaCount = if (statsType == StatsType.Novels) allNovels.size else filteredLibraryManga.size,
                completedMangaCount = calculateCompletedCount(filteredLibraryManga, allNovels, statsType),
                totalReadDuration = totalReadDuration,
                readingStreak = streak,
                historyPoints = historyPoints,
                avgDurationPerDay = avgDurationPerDay,
                ankiCardsAdded = totalAnkiCards,
                charactersRead = totalChars,
                charactersPerHour = charactersPerHour,
            )

            val titlesStatData = StatsData.Titles(
                startedMangaCount = calculateStartedCount(filteredLibraryManga, allNovelStatsList, statsType),
                localMangaCount = when (statsType) {
                    StatsType.All -> filteredLibraryManga.count { it.manga.isLocal() } + allNovels.size
                    StatsType.Manga -> filteredLibraryManga.count { it.manga.isLocal() }
                    StatsType.Novels -> allNovels.size
                },
            )

            // Chapter calculations for novels
            var novelTotalChapters = 0
            var novelReadChapters = 0
            if (statsType == StatsType.All || statsType == StatsType.Novels) {
                allNovels.forEach { novel ->
                    if (activeProfileId == null || novelProfileMap[novel.id] == activeProfileId) {
                        val bookDir = BookStorage.getBookDirectory(context, novel.id)
                        val info = BookStorage.loadBookInfo(bookDir)
                        val bookmark = BookStorage.loadBookmark(bookDir)
                        novelTotalChapters += info?.chapterInfo?.size ?: 0
                        novelReadChapters += bookmark?.chapterIndex ?: 0
                    }
                }
            }

            val chapterStatData = StatsData.Chapters(
                totalChapterCount = when (statsType) {
                    StatsType.All -> filteredLibraryManga.sumOf { it.totalChapters }.toInt() + novelTotalChapters
                    StatsType.Manga -> filteredLibraryManga.sumOf { it.totalChapters }.toInt()
                    StatsType.Novels -> novelTotalChapters
                },
                readChapterCount = when (statsType) {
                    StatsType.All -> filteredLibraryManga.sumOf { it.readCount }.toInt() + novelReadChapters
                    StatsType.Manga -> filteredLibraryManga.sumOf { it.readCount }.toInt()
                    StatsType.Novels -> novelReadChapters
                },
                downloadCount = when (statsType) {
                    StatsType.All -> filteredLibraryManga.sumOf { downloadManager.getDownloadCount(it.manga) }
                    StatsType.Manga -> filteredLibraryManga.sumOf { downloadManager.getDownloadCount(it.manga) }
                    StatsType.Novels -> 0 // Hide/Ignore for novels as requested
                },
            )

            val trackers = getMangaTrackMap(filteredLibraryManga)
            val scoredTrackers = getScoredMangaTrackMap(trackers)
            val trackerStatData = StatsData.Trackers(
                trackedTitleCount = trackers.size,
                meanScore = getTrackMeanScore(scoredTrackers),
                trackerCount = loggedInTrackers.size,
            )


            mutableState.update {
                StatsScreenState.Success(
                    overview = overviewStatData,
                    titles = titlesStatData,
                    chapters = chapterStatData,
                    trackers = trackerStatData,
                    dateScale = dateScale,
                    dateOffset = dateOffset,
                    statsType = statsType,
                    activeProfileId = activeProfileId,
                    profiles = profilesList,
                )
            }
        }.onEach { }.launchIn(screenModelScope)
    }

    private fun getDateRange(scale: StatsDateScale, offset: Int): Pair<LocalDate, LocalDate> {
        val now = LocalDate.now()
        return when (scale) {
            StatsDateScale.Day -> {
                val date = now.plusDays(offset.toLong())
                date to date
            }
            StatsDateScale.Week -> {
                val weekOffset = offset.toLong()
                val start = now.plusWeeks(weekOffset).with(DayOfWeek.MONDAY)
                val end = start.plusDays(6)
                start to end
            }
            StatsDateScale.Month -> {
                val monthOffset = offset.toLong()
                val start = now.plusMonths(monthOffset).withDayOfMonth(1)
                val end = start.plusMonths(1).minusDays(1)
                start to end
            }
            StatsDateScale.Year -> {
                val yearOffset = offset.toLong()
                val start = now.plusYears(yearOffset).withDayOfYear(1)
                val end = start.plusYears(1).minusDays(1)
                start to end
            }
            StatsDateScale.AllTime -> {
                LocalDate.of(2000, 1, 1) to now
            }
        }
    }

    private fun filterSessionsByScale(sessions: List<ReadingSession>, scale: StatsDateScale, offset: Int): List<ReadingSession> {
        if (scale == StatsDateScale.AllTime) return sessions
        val (start, end) = getDateRange(scale, offset)
        val startMillis = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return sessions.filter {
            it.readAt.time in startMillis until endMillis
        }
    }

    private fun filterNovelStatsByScale(stats: List<Statistics>, scale: StatsDateScale, offset: Int): List<Statistics> {
        if (scale == StatsDateScale.AllTime) return stats
        val (start, end) = getDateRange(scale, offset)
        val startStr = start.toString()
        val endStr = end.toString()
        return stats.filter { it.dateKey in startStr..endStr }
    }

    private fun filterAnkiStatsByScale(stats: List<com.canopus.chimareader.data.AnkiStats>, scale: StatsDateScale, offset: Int): List<com.canopus.chimareader.data.AnkiStats> {
        if (scale == StatsDateScale.AllTime) return stats
        val (start, end) = getDateRange(scale, offset)
        val startStr = start.toString()
        val endStr = end.toString()
        return stats.filter { it.dateKey in startStr..endStr }
    }

    private fun filterMangaStatsByScale(stats: List<com.canopus.chimareader.data.MangaStats>, scale: StatsDateScale, offset: Int): List<com.canopus.chimareader.data.MangaStats> {
        if (scale == StatsDateScale.AllTime) return stats
        val (start, end) = getDateRange(scale, offset)
        val startStr = start.toString()
        val endStr = end.toString()
        return stats.filter { it.dateKey in startStr..endStr }
    }

    private fun calculateStreak(mangaStats: List<com.canopus.chimareader.data.MangaStats>, novelStats: List<Statistics>): Int {
        val mangaDays = mangaStats.mapNotNull { 
            try { LocalDate.parse(it.dateKey) } catch (e: Exception) { null }
        }.toSet()
        val novelDays = novelStats.mapNotNull { 
            try { LocalDate.parse(it.dateKey) } catch (e: Exception) { null }
        }.toSet()
        
        val allDays = (mangaDays + novelDays).sortedDescending()
        if (allDays.isEmpty()) return 0
        
        var streak = 0
        var current = LocalDate.now()
        
        if (!allDays.contains(current)) {
            current = current.minusDays(1)
        }
        
        for (day in allDays) {
            if (day == current) {
                streak++
                current = current.minusDays(1)
            } else if (day.isBefore(current)) {
                break
            }
        }
        return streak
    }

    private fun calculateCompletedCount(libraryManga: List<LibraryManga>, novels: List<com.canopus.chimareader.data.BookMetadata>, type: StatsType): Int {
        val mangaCompleted = libraryManga.count {
            it.manga.status.toInt() == SManga.COMPLETED && it.unreadCount == 0L
        }
        val novelCompleted = 0 
        
        return when (type) {
            StatsType.All -> mangaCompleted + novelCompleted
            StatsType.Manga -> mangaCompleted
            StatsType.Novels -> novelCompleted
        }
    }

    private fun calculateStartedCount(libraryManga: List<LibraryManga>, novelStats: List<Statistics>, type: StatsType): Int {
        val mangaStarted = libraryManga.count { it.hasStarted }
        val novelStarted = novelStats.map { it.title }.distinct().size
        
        return when (type) {
            StatsType.All -> (libraryManga.filter { it.hasStarted }.map { it.manga.title } + novelStats.map { it.title }).distinct().size
            StatsType.Manga -> mangaStarted
            StatsType.Novels -> novelStarted
        }
    }

    private suspend fun getMangaTrackMap(libraryManga: List<LibraryManga>): Map<Long, List<Track>> {
        val loggedInTrackerIds = loggedInTrackers.map { it.id }.toHashSet()
        return libraryManga.mapNotNull { manga ->
            val tracks = getTracks.await(manga.id)
                .fastFilter { it.trackerId in loggedInTrackerIds }

            if (tracks.isEmpty()) null else manga.id to tracks
        }.toMap()
    }

    private fun getScoredMangaTrackMap(mangaTrackMap: Map<Long, List<Track>>): Map<Long, List<Track>> {
        return mangaTrackMap.mapNotNull { (mangaId, tracks) ->
            val trackList = tracks.mapNotNull { track ->
                track.takeIf { it.score > 0.0 }
            }
            if (trackList.isEmpty()) return@mapNotNull null
            mangaId to trackList
        }.toMap()
    }

    private fun getTrackMeanScore(scoredMangaTrackMap: Map<Long, List<Track>>): Double {
        return scoredMangaTrackMap
            .map { (_, tracks) ->
                tracks.map(::get10PointScore).average()
            }
            .fastFilter { !it.isNaN() }
            .average()
    }

    private fun get10PointScore(track: Track): Double {
        val service = trackerManager.get(track.trackerId)!!
        return service.get10PointScore(track)
    }

    fun toggleReadManga() {
        _allRead.update { !it }
    }

    fun setDateScale(scale: StatsDateScale) {
        _dateScale.value = scale
        _dateOffset.value = 0
    }

    fun setDateOffset(offset: Int) {
        _dateOffset.value = offset
    }

    fun setStatsType(type: StatsType) {
        _statsType.value = type
    }

    private fun calculateHistoryPoints(
        mangaStats: List<com.canopus.chimareader.data.MangaStats>,
        novelStats: List<Statistics>,
        scale: StatsDateScale,
        offset: Int,
    ): List<StatsData.HistoryPoint> {
        val now = LocalDate.now()
        val (start, _) = getDateRange(scale, offset)

        return when (scale) {
            StatsDateScale.Day -> {
                // Snap to the week containing the selected day (Mon-Sun)
                val weekStart = start.with(DayOfWeek.MONDAY)
                (0..6).map { daysIntoWeek ->
                    val date = weekStart.plusDays(daysIntoWeek.toLong())
                    val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    val value = aggregateForDate(date, mangaStats, novelStats)
                    val pOffset = ChronoUnit.DAYS.between(now, date).toInt()
                    StatsData.HistoryPoint(label, value, pOffset)
                }
            }
            StatsDateScale.Week -> {
                // Snap to the month containing the selected week (using Thursday to determine the month)
                val monthStart = start.plusDays(3).withDayOfMonth(1)
                val monthEnd = monthStart.plusMonths(1).minusDays(1)
                
                // Calculate weeks from the Monday of the first week of the month 
                // to the Monday of the week containing the end of the month
                val firstMonday = monthStart.with(DayOfWeek.MONDAY)
                val lastMonday = monthEnd.with(DayOfWeek.MONDAY)
                val weeksInMonth = (ChronoUnit.WEEKS.between(firstMonday, lastMonday).toInt() + 1).coerceAtLeast(4)
                
                (0 until weeksInMonth).map { weeksIntoMonth ->
                    val wStart = firstMonday.plusWeeks(weeksIntoMonth.toLong())
                    val wEnd = wStart.plusDays(6)
                    val label = "W${wStart.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
                    val value = aggregateForRange(wStart, wEnd, mangaStats, novelStats)
                    val pOffset = ChronoUnit.WEEKS.between(
                        now.with(DayOfWeek.MONDAY),
                        wStart
                    ).toInt()
                    StatsData.HistoryPoint(label, value, pOffset)
                }
            }
            StatsDateScale.Month -> {
                // Snap to the year containing the selected month
                val yearStart = start.withDayOfYear(1)
                (0..11).map { monthsIntoYear ->
                    val mDate = yearStart.plusMonths(monthsIntoYear.toLong())
                    val label = mDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    val value = aggregateForMonth(mDate, mangaStats, novelStats)
                    val pOffset = ChronoUnit.MONTHS.between(
                        now.withDayOfMonth(1),
                        mDate.withDayOfMonth(1)
                    ).toInt()
                    StatsData.HistoryPoint(label, value, pOffset)
                }
            }
            StatsDateScale.Year, StatsDateScale.AllTime -> {
                (0..4).reversed().map { yearsAgo ->
                    val yDate = now.minusYears(yearsAgo.toLong())
                    val label = yDate.year.toString()
                    val yStart = yDate.withDayOfYear(1)
                    val yEnd = yDate.withDayOfYear(yDate.lengthOfYear())
                    val value = aggregateForRange(yStart, yEnd, mangaStats, novelStats)
                    val pOffset = -yearsAgo
                    StatsData.HistoryPoint(label, value, pOffset)
                }
            }
        }
    }

    private fun aggregateForDate(
        date: LocalDate,
        mangaStats: List<com.canopus.chimareader.data.MangaStats>,
        novelStats: List<Statistics>,
    ): Long {
        val dateKey = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val mangaValue = mangaStats
            .filter { it.dateKey == dateKey }
            .sumOf { it.readingTime }

        val novelValue = novelStats
            .filter { it.dateKey == dateKey }
            .sumOf { (it.readingTime * 1000).toLong() }

        return mangaValue + novelValue
    }

    private fun aggregateForRange(
        start: LocalDate,
        end: LocalDate,
        mangaStats: List<com.canopus.chimareader.data.MangaStats>,
        novelStats: List<Statistics>,
    ): Long {
        val startStr = start.toString()
        val endStr = end.toString()
        
        val mangaValue = mangaStats
            .filter { it.dateKey in startStr..endStr }
            .sumOf { it.readingTime }

        val novelValue = novelStats
            .filter { it.dateKey in startStr..endStr }
            .sumOf { (it.readingTime * 1000).toLong() }

        return mangaValue + novelValue
    }

    private fun aggregateForMonth(
        monthDate: LocalDate,
        mangaStats: List<com.canopus.chimareader.data.MangaStats>,
        novelStats: List<Statistics>,
    ): Long {
        val yearMonth = YearMonth.from(monthDate)
        
        val mangaValue = mangaStats
            .filter { s ->
                try {
                    val entryDate = LocalDate.parse(s.dateKey)
                    YearMonth.from(entryDate) == yearMonth
                } catch (e: Exception) {
                    false
                }
            }
            .sumOf { it.readingTime }

        val novelValue = novelStats
            .filter { s ->
                try {
                    val entryDate = LocalDate.parse(s.dateKey)
                    YearMonth.from(entryDate) == yearMonth
                } catch (e: Exception) {
                    false
                }
            }
            .sumOf { (it.readingTime * 1000).toLong() }

        return mangaValue + novelValue
    }
}
