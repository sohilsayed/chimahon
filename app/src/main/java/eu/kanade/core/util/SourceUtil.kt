package eu.kanade.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import tachiyomi.domain.animesource.service.AnimeSourceManager
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ifSourcesLoaded(): Boolean {
    return remember { Injekt.get<SourceManager>().isInitialized }.collectAsState().value
}

@Composable
fun ifAnimeSourcesLoaded(): Boolean {
    return remember { Injekt.get<AnimeSourceManager>().isInitialized }.collectAsState().value
}
