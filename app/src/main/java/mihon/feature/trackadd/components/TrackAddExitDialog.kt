package mihon.feature.trackadd.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TrackAddExitDialog(
    onDismissRequest: () -> Unit,
    exitTrackAdd: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Stop adding to tracker?")
        },
        confirmButton = {
            TextButton(onClick = exitTrackAdd) {
                Text(text = stringResource(MR.strings.action_stop))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
