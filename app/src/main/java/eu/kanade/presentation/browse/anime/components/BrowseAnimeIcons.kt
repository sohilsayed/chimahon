package eu.kanade.presentation.browse.anime.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.icon
import eu.kanade.tachiyomi.R
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.source.local.entries.anime.LocalAnimeSource

private val defaultModifier = Modifier
    .height(40.dp)
    .aspectRatio(1f)

@Composable
fun AnimeSourceIcon(
    source: AnimeSource,
    modifier: Modifier = Modifier,
) {
    val icon = source.icon

    when {
        source.isStub && icon == null -> {
            Image(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                modifier = modifier.then(defaultModifier),
            )
        }
        icon != null -> {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = modifier.then(defaultModifier),
            )
        }
        source.id == LocalAnimeSource.ID -> {
            Image(
                painter = painterResource(R.mipmap.ic_local_source),
                contentDescription = null,
                modifier = modifier.then(defaultModifier),
            )
        }
        else -> {
            Image(
                painter = painterResource(R.mipmap.ic_default_source),
                contentDescription = null,
                modifier = modifier.then(defaultModifier),
            )
        }
    }
}
