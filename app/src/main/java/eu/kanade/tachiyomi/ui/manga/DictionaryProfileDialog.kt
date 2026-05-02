package eu.kanade.tachiyomi.ui.manga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import chimahon.dictionary.DictionaryProfile
import chimahon.dictionary.DictionaryProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.RadioButtonItem
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun DictionaryProfileDialog(
    mangaId: Long?,
    sourceId: Long?,
    onDismissRequest: () -> Unit,
) {
    val repository = remember { Injekt.get<DictionaryProfileRepository>() }
    val scope = rememberCoroutineScope()
    
    var profiles by remember { mutableStateOf<List<DictionaryProfile>>(emptyList()) }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        profiles = repository.getProfiles().first()
        if (mangaId != null) {
            selectedProfileId = repository.getProfileByManga(mangaId)?.id
        } else if (sourceId != null) {
            selectedProfileId = repository.getProfileBySource(sourceId)?.id
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(MR.strings.pref_dictionary_profile))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                RadioButtonItem(
                    label = stringResource(MR.strings.label_default),
                    selected = selectedProfileId == null,
                    onClick = { selectedProfileId = null },
                )
                
                profiles.forEach { profile ->
                    RadioButtonItem(
                        label = profile.name,
                        selected = selectedProfileId == profile.id,
                        onClick = { selectedProfileId = profile.id },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        if (mangaId != null) {
                            repository.setMangaProfile(mangaId, selectedProfileId)
                        } else if (sourceId != null) {
                            repository.setSourceProfile(sourceId, selectedProfileId)
                        }
                        onDismissRequest()
                    }
                }
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        }
    )
}
