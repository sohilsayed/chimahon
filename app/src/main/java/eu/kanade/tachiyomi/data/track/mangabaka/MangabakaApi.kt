package eu.kanade.tachiyomi.data.track.mangabaka

import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItemResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaListResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaSearchResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaUserProfile
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaUserProfileResponse
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import tachiyomi.domain.track.model.Track as DomainTrack

class MangaBakaApi(
    private val trackId: Long,
    baseClient: OkHttpClient,
    interceptor: MangaBakaInterceptor,
) {

    private val json: Json by injectLazy()

    private val client = baseClient.newBuilder().addInterceptor {
        it.request().newBuilder()
            .header(
                "User-Agent",
                buildString {
                    append("${MR.strings.app_name}/v${BuildConfig.VERSION_NAME} ")
                    append("(${BuildConfig.APPLICATION_ID} ${BuildConfig.COMMIT_SHA}) ")
                    append("(Android) (https://github.com/mihonapp/mihon)")
                },
            )
            .build()
            .let(it::proceed)
    }.build()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun getProfile(): MangaBakaUserProfile {
        return withIOContext {
            with(json) {
                authClient.newCall(GET("$API_BASE_URL/v1/my/profile"))
                    .awaitSuccess()
                    .parseAs<MangaBakaUserProfileResponse>()
                    .data
            }
        }
    }

    suspend fun addLibManga(track: Track): Track {
        return withIOContext {
            val url = "$LIBRARY_API_URL/${track.remote_id}"
            val body = buildJsonObject {
                put("is_private", track.private)
                put("state", track.toApiStatus())
                if (track.last_chapter_read > 0.0) {
                    put("progress_chapter", track.last_chapter_read)
                }
                if (track.score > 0) {
                    put("rating", track.score.toInt().coerceIn(0, 100))
                }
                if (track.started_reading_date > 0) {
                    put("start_date", track.started_reading_date.toApiDateTimeString())
                }
                if (track.finished_reading_date > 0) {
                    put("finish_date", track.finished_reading_date.toApiDateTimeString())
                }
            }
                .toString()
                .toRequestBody()

            authClient
                .newCall(POST(url, body = body, headers = headersOf("Content-Type", APP_JSON)))
                .awaitSuccess()

            track
        }
    }

    suspend fun deleteLibManga(track: DomainTrack) {
        withIOContext {
            authClient
                .newCall(DELETE("$LIBRARY_API_URL/${track.remoteId}"))
                .awaitSuccess()
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        return withIOContext {
            with(json) {
                try {
                    val userData = authClient.newCall(GET("$LIBRARY_API_URL/${track.remote_id}"))
                        .awaitSuccess()
                        .parseAs<MangaBakaListResult>()
                        .data

                    val additionalData = authClient.newCall(GET("$API_BASE_URL/v1/series/${track.remote_id}"))
                        .awaitSuccess()
                        .parseAs<MangaBakaItemResult>()
                        .data

                    Track.create(TrackerManager.MANGABAKA).apply {
                        remote_id = track.remote_id
                        title = additionalData.title
                        status = userData.getStatus()
                        score = userData.rating?.toDouble() ?: 0.0
                        started_reading_date = parseApiDateMillis(userData.startDate)
                        finished_reading_date = parseApiDateMillis(userData.finishDate)
                        last_chapter_read = userData.progressChapter ?: 0.0
                        private = userData.isPrivate
                    }
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        null
                    } else {
                        throw e
                    }
                }
            }
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withIOContext {
            val url = "$LIBRARY_API_URL/${track.remote_id}"
            val body = buildJsonObject {
                put("state", track.toApiStatus())
                put("is_private", track.private)
                if (track.last_chapter_read > 0.0) {
                    put("progress_chapter", track.last_chapter_read)
                } else {
                    put("progress_chapter", null)
                }
                if (track.score > 0) {
                    put("rating", track.score.toInt().coerceIn(0, 100))
                } else {
                    put("rating", null)
                }
                if (track.started_reading_date > 0) {
                    put("start_date", track.started_reading_date.toApiDateTimeString())
                } else {
                    put("start_date", null)
                }
                if (track.finished_reading_date > 0) {
                    put("finish_date", track.finished_reading_date.toApiDateTimeString())
                } else {
                    put("finish_date", null)
                }
            }
                .toString()
                .toRequestBody()

            authClient
                .newCall(PUT(url, body = body, headers = headersOf("Content-Type", APP_JSON)))
                .awaitSuccess()

            track
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withIOContext {
            val url = "$API_BASE_URL/v1/series/search".toUri().buildUpon()
                .appendQueryParameter("q", search)
                .appendQueryParameter("type_not", "novel")
                .build()
            with(json) {
                client.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MangaBakaSearchResult>()
                    .data
                    .map { parseSearchItem(it) }
            }
        }
    }

    private fun parseSearchItem(item: MangaBakaItem): TrackSearch {
        return TrackSearch.create(trackId).apply {
            remote_id = item.id
            title = item.title
            summary = item.description?.trim().orEmpty()
            score = item.rating?.toBigDecimal()?.setScale(2, RoundingMode.HALF_UP)?.toDouble() ?: -1.0
            cover_url = item.cover.x250.x1.orEmpty()
            tracking_url = "$BASE_URL/${item.id}"
            start_date = item.published.startDate.orEmpty()
            publishing_status = item.status
            publishing_type = item.type.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
            }
            authors = item.authors.orEmpty()
            artists = item.artists.orEmpty()
        }
    }

    suspend fun getMangaDetails(id: Long): TrackSearch? {
        return withIOContext {
            val url = "$API_BASE_URL/v1/series/$id"
            with(json) {
                try {
                    authClient.newCall(GET(url))
                        .awaitSuccess()
                        .parseAs<MangaBakaItemResult>()
                        .data
                        .let { parseSearchItem(it) }
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        return@with null
                    }
                    throw e
                }
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://mangabaka.org"
        private const val API_BASE_URL = "https://api.mangabaka.org"
        private const val LIBRARY_API_URL = "$API_BASE_URL/v1/my/library"
        private const val APP_JSON = "application/json"

        private fun Long.toApiDateTimeString(): String {
            return toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toString()
        }

        private fun parseApiDateMillis(value: String?): Long {
            val text = value?.takeIf { it.isNotBlank() } ?: return 0

            return runCatching {
                LocalDate.parse(text)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrElse {
                runCatching {
                    Instant.parse(text).toEpochMilli()
                }.getOrElse {
                    runCatching {
                        OffsetDateTime.parse(text).toInstant().toEpochMilli()
                    }.getOrDefault(0L)
                }
            }
        }
    }
}
