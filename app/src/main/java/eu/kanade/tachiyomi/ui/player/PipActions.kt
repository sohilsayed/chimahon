package eu.kanade.tachiyomi.ui.player

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import eu.kanade.tachiyomi.R
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

const val PIP_INTENTS_FILTER = "pip_control"
const val PIP_INTENT_ACTION = "media_control"
const val PIP_PAUSE = 1
const val PIP_PLAY = 2
const val PIP_PREVIOUS = 3
const val PIP_NEXT = 4

fun createPipActions(
    context: Context,
    isPaused: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
): ArrayList<RemoteAction> = arrayListOf(
    createPipAction(context, R.drawable.ic_skip_previous_24dp, context.stringResource(MR.strings.player_previous), PIP_PREVIOUS, hasPrevious),
    if (isPaused) {
        createPipAction(context, R.drawable.ic_play_arrow_24dp, context.stringResource(MR.strings.action_play), PIP_PLAY)
    } else {
        createPipAction(context, R.drawable.ic_pause_24dp, context.stringResource(MR.strings.action_pause), PIP_PAUSE)
    },
    createPipAction(context, R.drawable.ic_skip_next_24dp, context.stringResource(MR.strings.player_next), PIP_NEXT, hasNext),
)

private fun createPipAction(
    context: Context,
    iconRes: Int,
    title: String,
    requestCode: Int,
    isEnabled: Boolean = true,
): RemoteAction {
    val action = RemoteAction(
        Icon.createWithResource(context, iconRes),
        title,
        title,
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(PIP_INTENTS_FILTER)
                .putExtra(PIP_INTENT_ACTION, requestCode)
                .setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        ),
    )
    action.isEnabled = isEnabled
    return action
}
