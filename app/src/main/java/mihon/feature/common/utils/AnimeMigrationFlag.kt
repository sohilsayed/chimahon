package mihon.feature.common.utils

import dev.icerock.moko.resources.StringResource
import mihon.domain.animemigration.models.AnimeMigrationFlag
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

fun AnimeMigrationFlag.getLabel(): StringResource {
    return when (this) {
        AnimeMigrationFlag.EPISODE -> MR.strings.episodes
        AnimeMigrationFlag.CATEGORY -> MR.strings.categories
        AnimeMigrationFlag.TRACK -> MR.strings.track
        AnimeMigrationFlag.CUSTOM_BACKGROUND -> MR.strings.action_edit_background
        AnimeMigrationFlag.CUSTOM_COVER -> MR.strings.custom_cover
        AnimeMigrationFlag.REMOVE_DOWNLOAD -> MR.strings.delete_downloaded
        AnimeMigrationFlag.EXTRA -> SYMR.strings.log_extra
    }
}
