package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Passive OCR indicator - shows OCR status without click functionality.
 * OCR actions are now handled through the download indicator dropdown.
 */
@Composable
fun ChapterOcrIndicator(
    isOcrReady: Boolean,
    isOcrRunning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isOcrRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = if (isOcrReady) Icons.Filled.Search else Icons.Outlined.Search,
                contentDescription = if (isOcrReady) {
                    stringResource(MR.strings.ocr_ready)
                } else {
                    stringResource(MR.strings.ocr_not_ready)
                },
                modifier = Modifier.size(24.dp),
                tint = if (isOcrReady) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}

