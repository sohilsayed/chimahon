package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    if (!isOcrReady && !isOcrRunning) {
        return
    }

    val contentDescription = if (isOcrRunning) {
        stringResource(MR.strings.ocr_running)
    } else {
        stringResource(MR.strings.ocr_ready)
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isOcrRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = "OCR",
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 8.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
