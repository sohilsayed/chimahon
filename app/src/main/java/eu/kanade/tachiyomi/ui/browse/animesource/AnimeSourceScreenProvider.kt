package eu.kanade.tachiyomi.ui.browse.animesource

import cafe.adriel.voyager.core.screen.Screen

interface AnimeSourceScreenProvider {
    fun createBrowseScreen(listingQuery: String?): Screen
}

interface AlwaysVisibleAnimeSource
