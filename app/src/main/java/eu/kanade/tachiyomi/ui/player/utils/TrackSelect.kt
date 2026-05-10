package eu.kanade.tachiyomi.ui.player.utils

import androidx.core.os.LocaleListCompat
import eu.kanade.tachiyomi.ui.player.mpv.MPVView
import eu.kanade.tachiyomi.ui.player.setting.AudioPreferences
import eu.kanade.tachiyomi.ui.player.setting.SubtitlePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

class TrackSelect(
    private val subtitlePreferences: SubtitlePreferences = Injekt.get(),
    private val audioPreferences: AudioPreferences = Injekt.get(),
) {

    fun getPreferredSubTrack(tracks: List<MPVView.Track>): MPVView.Track? {
        return getPreferredTrack(tracks, subtitlePreferences.preferredSubLanguages().get())
    }

    fun getPreferredAudioTrack(tracks: List<MPVView.Track>): MPVView.Track? {
        return getPreferredTrack(tracks, audioPreferences.preferredAudioLanguages().get())
    }

    private fun getPreferredTrack(tracks: List<MPVView.Track>, prefLangsStr: String): MPVView.Track? {
        if (tracks.isEmpty()) return null

        val prefLangs = prefLangsStr.split(",").filter { it.isNotEmpty() }
        val locales = prefLangs.map { Locale(it) }.ifEmpty {
            listOf(LocaleListCompat.getDefault()[0] ?: Locale.getDefault())
        }

        val matchers = locales.mapNotNull { locale ->
            if (locale.language.isNullOrEmpty()) return@mapNotNull null
            val englishName = locale.getDisplayName(Locale.ENGLISH).substringBefore(" (")
            val iso3 = try { locale.isO3Language } catch (_: Exception) { null }
            val pattern = listOfNotNull(iso3, locale.language).joinToString("|")
            val langRegex = Regex("""\b$pattern\b""", RegexOption.IGNORE_CASE)
            Triple(locale, englishName, langRegex)
        }

        val chosen = matchers.firstOrNull { (_, name, regex) ->
            tracks.any { matchesLang(it, name, regex) }
        } ?: return null

        return tracks.firstOrNull { matchesLang(it, chosen.second, chosen.third) }
    }

    private fun matchesLang(track: MPVView.Track, englishName: String, langRegex: Regex): Boolean {
        return track.name.contains(englishName, ignoreCase = true) ||
            track.language?.let { langRegex.containsMatchIn(it) } == true
    }
}
