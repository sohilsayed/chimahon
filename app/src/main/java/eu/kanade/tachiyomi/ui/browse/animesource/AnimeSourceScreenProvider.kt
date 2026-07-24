package eu.kanade.tachiyomi.ui.browse.animesource

import cafe.adriel.voyager.core.screen.Screen

interface AnimeSourceScreenProvider {
    fun createBrowseScreen(listingQuery: String?, targetUrl: String?): Screen
}

interface AlwaysVisibleAnimeSource
