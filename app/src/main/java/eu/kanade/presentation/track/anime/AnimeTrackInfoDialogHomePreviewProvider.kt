package eu.kanade.presentation.track.anime

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackItem
import tachiyomi.domain.track.anime.model.AnimeTrack
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class AnimeTrackInfoDialogHomePreviewProvider :
    PreviewParameterProvider<@Composable () -> Unit> {

    private val aTrack = AnimeTrack(
        id = 1L,
        animeId = 2L,
        trackerId = 3L,
        remoteId = 4L,
        libraryId = null,
        title = "Manage Name On Tracker Site",
        lastEpisodeSeen = 2.0,
        totalEpisodes = 12L,
        status = 1L,
        score = 2.0,
        remoteUrl = "https://example.com",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )
    private val privateTrack = aTrack.copy(private = true)
    private val trackItemWithoutTrack = AnimeTrackItem(
        track = null,
        tracker = MyAnimeList(1L),
    )
    private val trackItemWithTrack = AnimeTrackItem(
        track = aTrack,
        tracker = Anilist(2L),
    )
    private val trackItemWithPrivateTrack = AnimeTrackItem(
        track = privateTrack,
        tracker = Anilist(2L),
    )

    private val trackersWithAndWithoutTrack = @Composable {
        AnimeTrackInfoDialogHome(
            trackItems = listOf(
                trackItemWithoutTrack,
                trackItemWithTrack,
            ),
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
            onStatusClick = {},
            onEpisodeClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    private val noTrackers = @Composable {
        AnimeTrackInfoDialogHome(
            trackItems = listOf(),
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
            onStatusClick = {},
            onEpisodeClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    private val trackerWithPrivateTracking = @Composable {
        AnimeTrackInfoDialogHome(
            trackItems = listOf(trackItemWithPrivateTrack),
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
            onStatusClick = {},
            onEpisodeClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    override val values: Sequence<@Composable () -> Unit>
        get() = sequenceOf(
            trackersWithAndWithoutTrack,
            noTrackers,
            trackerWithPrivateTracking,
        )
}
