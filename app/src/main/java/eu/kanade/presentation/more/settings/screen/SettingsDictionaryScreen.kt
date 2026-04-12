package eu.kanade.presentation.more.settings.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import chimahon.HoshiDicts
import chimahon.anki.AnkiCardCreator
import chimahon.anki.AnkiDroidBridge
import chimahon.anki.Marker
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Collections.emptyList

private const val TAG = "DictionaryImport"

private val _dictionaryNames = MutableStateFlow<List<String>>(emptyList())
private val dictionaryNames = _dictionaryNames.asStateFlow()

private val markerDisplayLabels: Map<String, String> = Marker.ALL_WITH_TODO.associateWith { marker ->
    val isTodo = marker in Marker.TODO_MARKERS
    val prefix = if (isTodo) "" else ""
    when (marker) {
        Marker.EXPRESSION -> "${prefix}Expression"
        Marker.READING -> "${prefix}Reading"
        Marker.FURIGANA -> "${prefix}Furigana"
        Marker.FURIGANA_PLAIN -> "${prefix}Furigana Plain"
        Marker.GLOSSARY -> "${prefix}Glossary"
        Marker.GLOSSARY_BRIEF -> "${prefix}Glossary Brief"
        Marker.GLOSSARY_PLAIN -> "${prefix}Glossary Plain"
        Marker.GLOSSARY_NO_DICT -> "${prefix}Glossary No Dict"
        Marker.GLOSSARY_FIRST -> "${prefix}Glossary First"
        Marker.GLOSSARY_FIRST_BRIEF -> "${prefix}Glossary First Brief"
        Marker.SENTENCE -> "${prefix}Sentence"
        Marker.CLOZE_PREFIX -> "${prefix}Cloze Prefix"
        Marker.CLOZE_BODY -> "${prefix}Cloze Body"
        Marker.CLOZE_BODY_KANA -> "${prefix}Cloze Body Kana"
        Marker.CLOZE_SUFFIX -> "${prefix}Cloze Suffix"
        Marker.TAGS -> "${prefix}Tags"
        Marker.PART_OF_SPEECH -> "${prefix}Part of Speech"
        Marker.CONJUGATION -> "${prefix}Conjugation"
        Marker.DICTIONARY -> "${prefix}Dictionary"
        Marker.DICTIONARY_ALIAS -> "${prefix}Dictionary Alias"
        Marker.FREQUENCIES -> "${prefix}Frequencies"
        Marker.FREQUENCY_HARMONIC_RANK -> "${prefix}Freq Harmonic"
        Marker.FREQUENCY_AVERAGE_RANK -> "${prefix}Freq Average"
        Marker.PITCH_ACCENTS -> "${prefix}Pitch Accents"
        Marker.PITCH_ACCENT_POSITIONS -> "${prefix}Pitch Positions"
        Marker.PITCH_ACCENT_CATEGORIES -> "${prefix}Pitch Categories"
        Marker.AUDIO -> "${prefix}Audio"
        Marker.SCREENSHOT -> "${prefix}Screenshot"
        Marker.SEARCH_QUERY -> "${prefix}Search Query"
        Marker.MANGA -> "${prefix}Manga"
        Marker.CHAPTER -> "${prefix}Chapter"
        Marker.MEDIA -> "${prefix}Media"
        Marker.SINGLE_GLOSSARY -> "${prefix}Single Glossary ▸"
        else -> marker
    }
}

private fun loadDictionaryList(context: Context) {
    Log.d(TAG, "loadDictionaryList: called")
    val dictionariesDir = File(context.getExternalFilesDir(null), "dictionaries")
    val names = if (dictionariesDir.exists()) {
        dictionariesDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { it.name }
            .orEmpty()
    } else {
        emptyList()
    }
    Log.d(TAG, "loadDictionaryList: found ${names.size} dictionaries: $names")
    _dictionaryNames.value = names
}

object SettingsDictionaryScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_dictionary

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            loadDictionaryList(context)
        }
        return listOf(
            getAppearanceGroup(),
            getImportGroup(),
            getDictionaryListGroup(),
            getAnkiGroup(),
        )
    }

    @Composable
    private fun getAppearanceGroup(): Preference.PreferenceGroup {
        val dictionaryPreferences = Injekt.get<DictionaryPreferences>()

        val widthPref = dictionaryPreferences.popupWidth()
        val width by widthPref.collectAsState()

        val heightPref = dictionaryPreferences.popupHeight()
        val height by heightPref.collectAsState()

        val scalePref = dictionaryPreferences.popupScale()
        val scale by scalePref.collectAsState()

        val ocrBoxScalePref = dictionaryPreferences.ocrBoxScale()
        val ocrBoxScale by ocrBoxScalePref.collectAsState()

        val showFreqHarmonicPref = dictionaryPreferences.showFrequencyHarmonic()
        val showFreqHarmonic by showFreqHarmonicPref.collectAsState()

        val groupTermsPref = dictionaryPreferences.groupTerms()
        val groupTerms by groupTermsPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_dict_appearance),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = width,
                    title = stringResource(MR.strings.pref_dict_popup_width),
                    subtitle = "${width}px",
                    valueRange = 200..1920,
                    onValueChanged = { newValue ->
                        widthPref.set(newValue)
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = height,
                    title = stringResource(MR.strings.pref_dict_popup_height),
                    subtitle = "${height}px",
                    valueRange = 100..1080,
                    onValueChanged = { newValue ->
                        heightPref.set(newValue)
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = scale,
                    title = stringResource(MR.strings.pref_dict_popup_scale),
                    subtitle = "$scale%",
                    valueRange = 50..200,
                    onValueChanged = { newValue ->
                        scalePref.set(newValue)
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (ocrBoxScale * 100).toInt(),
                    title = stringResource(MR.strings.pref_dict_ocr_box_scale),
                    subtitle = String.format("%.1fx", ocrBoxScale),
                    valueRange = 50..200,
                    onValueChanged = { newValue ->
                        ocrBoxScalePref.set(newValue / 100f)
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showFreqHarmonicPref,
                    title = stringResource(MR.strings.pref_dict_show_frequency_harmonic),
                    subtitle = stringResource(MR.strings.pref_dict_show_frequency_harmonic_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = groupTermsPref,
                    title = stringResource(MR.strings.pref_dict_group_terms),
                    subtitle = stringResource(MR.strings.pref_dict_group_terms_summary),
                ),
            ),
        )
    }

    @Composable
    private fun getImportGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
        val orderPref = remember { dictionaryPreferences.dictionaryOrder() }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents(),
        ) { uris ->
            Log.d(TAG, "importLauncher: uris=${uris.size}")
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                uris.forEach { uri ->
                    Log.d(TAG, "importDictionaryFromUri: starting import for $uri...")
                    val result = importDictionaryFromUri(context, uri, orderPref.get())
                    Log.d(TAG, "importDictionaryFromUri: result=${result.first} success=${result.second}")

                    context.toast(result.first)
                }

                Log.d(TAG, "refreshing dictionary list after batch import")
                loadDictionaryList(context)
            }
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_import_dictionary),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_import_dictionary),
                    content = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        Log.d(TAG, "import button clicked")
                                        try {
                                            importLauncher.launch("application/zip")
                                        } catch (_: ActivityNotFoundException) {
                                            context.toast(MR.strings.file_picker_error)
                                        }
                                    }
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(MR.strings.pref_import_dictionary),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = stringResource(MR.strings.pref_import_dictionary_summ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.ImportExport,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDictionaryListGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
        val orderPref = remember { dictionaryPreferences.dictionaryOrder() }
        val currentOrder by orderPref.collectAsState()

        var dictToDelete by remember { mutableStateOf<String?>(null) }

        val dictionaries by dictionaryNames.collectAsState()

        val orderedDicts = remember(dictionaries, currentOrder) {
            val orderList = currentOrder.split(",").filter { it.isNotBlank() }
            val ordered = orderList.filter { it in dictionaries }
            val remaining = dictionaries.filter { it !in orderList }
            ordered + remaining
        }

        dictToDelete?.let { dictName ->
            AlertDialog(
                onDismissRequest = { dictToDelete = null },
                title = { Text(stringResource(MR.strings.pref_dict_delete)) },
                text = { Text("Delete \"$dictName\"? This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            Log.d(TAG, "deleting dictionary: $dictName")
                            val dictionariesDir = File(context.getExternalFilesDir(null), "dictionaries")
                            val dictDir = File(dictionariesDir, dictName)
                            if (dictDir.exists()) {
                                dictDir.deleteRecursively()
                            }
                            val newOrder = orderedDicts.filter { d -> d != dictName }.joinToString(",")
                            orderPref.set(newOrder)
                            loadDictionaryList(context)
                            dictToDelete = null
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dictToDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_dict_imported_list),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_dict_imported_list),
                    content = {
                        if (orderedDicts.isEmpty()) {
                            Text(
                                text = stringResource(MR.strings.pref_dict_none_imported),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            ) {
                                orderedDicts.forEachIndexed { index, dictName ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = dictName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val newList = orderedDicts.toMutableList()
                                                        val temp = newList[index]
                                                        newList[index] = newList[index - 1]
                                                        newList[index - 1] = temp
                                                        orderPref.set(newList.joinToString(","))
                                                    }
                                                },
                                                enabled = index > 0,
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.KeyboardArrowUp,
                                                    contentDescription = "Move up",
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (index < orderedDicts.size - 1) {
                                                        val newList = orderedDicts.toMutableList()
                                                        val temp = newList[index]
                                                        newList[index] = newList[index + 1]
                                                        newList[index + 1] = temp
                                                        orderPref.set(newList.joinToString(","))
                                                    }
                                                },
                                                enabled = index < orderedDicts.size - 1,
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                                    contentDescription = "Move down",
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                            IconButton(
                                                onClick = { dictToDelete = dictName },
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = stringResource(MR.strings.pref_dict_delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getAnkiGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val activity = context as? android.app.Activity
        val scope = rememberCoroutineScope()
        val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
        val bridge = remember { AnkiDroidBridge(context) }

        val enabledPref = remember { dictionaryPreferences.ankiEnabled() }
        val enabled by enabledPref.collectAsState()

        val dictionaries by dictionaryNames.collectAsState()

        var pendingPermissionCheck by remember { mutableStateOf(false) }

        // Check permission result when user returns from the system permission dialog
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && pendingPermissionCheck) {
                    pendingPermissionCheck = false
                    if (bridge.hasPermission()) {
                        enabledPref.set(true)
                    } else {
                        context.toast(MR.strings.pref_anki_permission_denied)
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val deckPref = remember { dictionaryPreferences.ankiDeck() }
        val selectedDeck by deckPref.collectAsState()

        val modelPref = remember { dictionaryPreferences.ankiModel() }
        val selectedModel by modelPref.collectAsState()

        val fieldMapPref = remember { dictionaryPreferences.ankiFieldMap() }
        val fieldMapJson by fieldMapPref.collectAsState()

        LaunchedEffect(fieldMapJson) {
            if (fieldMapJson.isBlank()) {
                fieldMapPref.set("{}")
            }
        }

        val dupCheckPref = remember { dictionaryPreferences.ankiDuplicateCheck() }
        val dupCheck by dupCheckPref.collectAsState()

        val dupScopePref = remember { dictionaryPreferences.ankiDuplicateScope() }
        val dupScope by dupScopePref.collectAsState()

        val dupActionPref = remember { dictionaryPreferences.ankiDuplicateAction() }
        val dupAction by dupActionPref.collectAsState()

        val tagsPref = remember { dictionaryPreferences.ankiDefaultTags() }
        val tags by tagsPref.collectAsState()

        val cropModePref = remember { dictionaryPreferences.ankiCropMode() }
        val cropMode by cropModePref.collectAsState()

        var decks by remember { mutableStateOf<List<String>>(emptyList()) }
        var models by remember { mutableStateOf<List<String>>(emptyList()) }
        var modelFields by remember { mutableStateOf<List<String>>(emptyList()) }
        var ankiInstalled by remember { mutableStateOf<Boolean?>(null) }
        var isLoading by remember { mutableStateOf(false) }

        val fieldMap = remember(fieldMapJson) {
            AnkiCardCreator.parseFieldMap(fieldMapJson)
        }
        val customFieldNames by remember(fieldMap, modelFields) {
            derivedStateOf {
                fieldMap.keys
                    .filter { mappedField -> modelFields.none { it.equals(mappedField, ignoreCase = true) } }
                    .sorted()
            }
        }
        var fieldMappingExpanded by remember { mutableStateOf(true) }
        var newCustomFieldName by remember { mutableStateOf("") }

        val setFieldValue: (String, String, Boolean) -> Unit = { fieldName, newDisplayValue, keepEmpty ->
            val normalizedName = fieldName.trim()
            if (normalizedName.isNotBlank()) {
                val updated = fieldMap.toMutableMap()
                val normalizedDisplayValue = newDisplayValue.trim()
                val effectiveDisplayValue = if (normalizedDisplayValue == "{}") "" else newDisplayValue
                if (effectiveDisplayValue.isBlank()) {
                    if (keepEmpty) {
                        updated[normalizedName] = ""
                    } else {
                        updated.remove(normalizedName)
                    }
                } else {
                    updated[normalizedName] = convertToStorageFormat(effectiveDisplayValue)
                }
                fieldMapPref.set(org.json.JSONObject(updated).toString())
            }
        }

        val removeCustomField: (String) -> Unit = { fieldName ->
            val updated = fieldMap.toMutableMap()
            updated.remove(fieldName)
            fieldMapPref.set(org.json.JSONObject(updated).toString())
        }

        // Check AnkiDroid status whenever screen is visible and enabled
        LaunchedEffect(enabled, selectedModel) {
            if (!enabled) {
                Log.d("AnkiSettings", "Anki disabled, skipping")
                return@LaunchedEffect
            }

            // Check installation
            if (ankiInstalled == null) {
                ankiInstalled = bridge.isAnkiDroidInstalled()
                Log.d("AnkiSettings", "AnkiDroid installed: $ankiInstalled")
            }

            // Check permission
            val hasPerm = bridge.hasPermission()
            Log.d("AnkiSettings", "Has permission: $hasPerm")

            // Must have permission to query
            if (ankiInstalled == true && hasPerm) {
                if (decks.isEmpty() || models.isEmpty()) {
                    isLoading = true
                    decks = bridge.deckNames()
                    models = bridge.modelNames()
                    isLoading = false
                    Log.d("AnkiSettings", "Loaded decks: ${decks.size}, models: ${models.size}")
                }
            } else if (ankiInstalled == true && !hasPerm) {
                Log.w("AnkiSettings", "AnkiDroid installed but no permission!")
            } else if (ankiInstalled == false) {
                Log.w("AnkiSettings", "AnkiDroid not installed")
            }
        }

        // Reload model fields when model changes or on screen refresh
        LaunchedEffect(selectedModel, ankiInstalled) {
            if (selectedModel.isNotBlank() && ankiInstalled == true && bridge.hasPermission()) {
                modelFields = bridge.modelFieldNames(selectedModel)

                // Only auto-detect if field map is empty (first-time setup)
                if (fieldMapJson.isBlank() || fieldMapJson == "{}") {
                    val detectedMap = modelFields.mapIndexedNotNull { index, fieldName ->
                        val marker = Marker.autoDetect(fieldName, index)
                        if (marker != null) fieldName to "{$marker}" else null
                    }.toMap()

                    if (detectedMap.isNotEmpty()) {
                        fieldMapPref.set(org.json.JSONObject(detectedMap).toString())
                    }
                }
            }
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_anki),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_anki),
                    content = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Enable toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(MR.strings.pref_anki_enable),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { checked ->
                                        if (!checked) {
                                            enabledPref.set(false)
                                            return@Switch
                                        }
                                        scope.launch {
                                            val installed = bridge.isAnkiDroidInstalled()
                                            if (!installed) {
                                                context.toast(MR.strings.pref_anki_no_ankidroid)
                                                return@launch
                                            }
                                            if (!bridge.hasPermission()) {
                                                if (activity != null) {
                                                    pendingPermissionCheck = true
                                                    bridge.requestPermission(activity)
                                                }
                                                return@launch
                                            }
                                            enabledPref.set(true)
                                        }
                                    },
                                )
                            }

                            if (!enabled) return@CustomPreference

                            if (ankiInstalled == false) {
                                Text(
                                    text = stringResource(MR.strings.pref_anki_no_ankidroid),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                return@CustomPreference
                            }

                            if (isLoading) {
                                Text(
                                    text = stringResource(MR.strings.pref_anki_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                return@CustomPreference
                            }

                            // Deck selector
                            AnkiDropdownPreference(
                                label = stringResource(MR.strings.pref_anki_deck),
                                value = selectedDeck,
                                options = decks,
                                onValueChange = { deckPref.set(it) },
                            )

                            // Model selector
                            AnkiDropdownPreference(
                                label = stringResource(MR.strings.pref_anki_model),
                                value = selectedModel,
                                options = models,
                                onValueChange = {
                                    if (it == selectedModel) {
                                        return@AnkiDropdownPreference
                                    }
                                    modelPref.set(it)
                                    fieldMapPref.set("{}")
                                },
                            )

                            // Field mapping
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { fieldMappingExpanded = !fieldMappingExpanded }
                                    .padding(top = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(MR.strings.pref_anki_field_mapping),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Icon(
                                        imageVector = if (fieldMappingExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = if (fieldMappingExpanded) "Collapse" else "Expand",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            if (fieldMappingExpanded) {
                                if (selectedModel.isNotBlank() && modelFields.isEmpty() && ankiInstalled == true) {
                                    Text(
                                        text = "Loading model fields...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                modelFields.forEach { fieldName ->
                                    val storageValue = fieldMap[fieldName] ?: ""
                                    val displayValue = storageValue
                                        .replace("<br>", "")
                                        .ifBlank { "{}" }
                                    AnkiFieldMappingRow(
                                        fieldName = fieldName,
                                        fieldValue = displayValue,
                                        onValueChange = { newDisplayValue ->
                                            setFieldValue(fieldName, newDisplayValue, false)
                                        },
                                        dictionaryNames = dictionaries,
                                    )
                                }

                                if (customFieldNames.isNotEmpty()) {
                                    Text(
                                        text = "Custom fields",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }

                                customFieldNames.forEach { fieldName ->
                                    val storageValue = fieldMap[fieldName] ?: ""
                                    val displayValue = storageValue
                                        .replace("<br>", "")
                                        .ifBlank { "{}" }
                                    AnkiFieldMappingRow(
                                        fieldName = fieldName,
                                        fieldValue = displayValue,
                                        onValueChange = { newDisplayValue ->
                                            setFieldValue(fieldName, newDisplayValue, true)
                                        },
                                        onDeleteField = {
                                            removeCustomField(fieldName)
                                        },
                                        dictionaryNames = dictionaries,
                                    )
                                }

                                OutlinedTextField(
                                    value = newCustomFieldName,
                                    onValueChange = { newCustomFieldName = it },
                                    label = { Text("Add custom field") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                )

                                TextButton(
                                    onClick = {
                                        val candidate = newCustomFieldName.trim()
                                        val alreadyExists = (modelFields + customFieldNames)
                                            .any { it.equals(candidate, ignoreCase = true) }
                                        if (candidate.isBlank() || alreadyExists) {
                                            return@TextButton
                                        }
                                        setFieldValue(candidate, "", true)
                                        newCustomFieldName = ""
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text("Add field")
                                }
                            }

                            // Duplicate handling
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(MR.strings.pref_anki_check_duplicates),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Switch(
                                    checked = dupCheck,
                                    onCheckedChange = { dupCheckPref.set(it) },
                                )
                            }

                            if (dupCheck) {
                                AnkiDropdownPreference(
                                    label = stringResource(MR.strings.pref_anki_duplicate_scope),
                                    value = if (dupScope == "all") "Everywhere" else "Deck only",
                                    options = listOf("deck", "all"),
                                    displayOptions = listOf("Deck only", "Everywhere"),
                                    onValueChange = { dupScopePref.set(it) },
                                )
                                AnkiDropdownPreference(
                                    label = stringResource(MR.strings.pref_anki_duplicate_action),
                                    value = when (dupAction) {
                                        "add" -> stringResource(MR.strings.pref_anki_duplicate_add)
                                        "overwrite" -> stringResource(MR.strings.pref_anki_duplicate_overwrite)
                                        "open" -> stringResource(MR.strings.pref_anki_duplicate_open)
                                        else -> stringResource(MR.strings.pref_anki_duplicate_prevent)
                                    },
                                    options = listOf("prevent", "add", "overwrite", "open"),
                                    displayOptions = listOf(
                                        stringResource(MR.strings.pref_anki_duplicate_prevent),
                                        stringResource(MR.strings.pref_anki_duplicate_add),
                                        stringResource(MR.strings.pref_anki_duplicate_overwrite),
                                        stringResource(MR.strings.pref_anki_duplicate_open),
                                    ),
                                    onValueChange = { dupActionPref.set(it) },
                                )
                            }

                            // Default tags
                            OutlinedTextField(
                                value = tags,
                                onValueChange = { tagsPref.set(it) },
                                label = { Text(stringResource(MR.strings.pref_anki_default_tags)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = { Text("Comma-separated tags") },
                            )

                            // Crop mode
                            var cropModeExpanded by remember { mutableStateOf(false) }
                            val cropModeOptions = listOf("full" to "Full Image", "crop" to "Crop Selection")
                            val cropModeDisplay = cropModeOptions.find { it.first == cropMode }?.second ?: "Full Image"

                            ExposedDropdownMenuBox(
                                expanded = cropModeExpanded,
                                onExpandedChange = { cropModeExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = cropModeDisplay,
                                    onValueChange = {},
                                    label = { Text("Screenshot Mode") },
                                    readOnly = true,
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    enabled = false,
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cropModeExpanded) },
                                )

                                ExposedDropdownMenu(
                                    expanded = cropModeExpanded,
                                    onDismissRequest = { cropModeExpanded = false },
                                ) {
                                    cropModeOptions.forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                cropModePref.set(value)
                                                cropModeExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun AnkiDropdownPreference(
        label: String,
        value: String,
        options: List<String>,
        displayOptions: List<String> = options,
        onValueChange: (String) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value.ifBlank { "" },
                onValueChange = {},
                label = { Text(label) },
                readOnly = true,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                enabled = false,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                options.zip(displayOptions).forEach { (opt, display) ->
                    DropdownMenuItem(
                        text = { Text(display) },
                        onClick = {
                            onValueChange(opt)
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AnkiFieldMappingRow(
        fieldName: String,
        fieldValue: String,
        onValueChange: (String) -> Unit,
        onDeleteField: (() -> Unit)? = null,
        dictionaryNames: List<String> = emptyList(),
    ) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        var singleGlossaryExpanded by remember { mutableStateOf(false) }
        val currentMarkers = remember(fieldValue) { parseMarkersForDisplay(fieldValue) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fieldName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (onDeleteField != null) {
                    IconButton(
                        onClick = onDeleteField,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove field",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Box {
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { newValue ->
                        onValueChange(newValue)
                    },
                    placeholder = { Text("{}", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                )

                IconButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 8.dp)
                        .size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Select markers",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier.width(200.dp),
            ) {
                Marker.ALL.forEach { marker ->
                    if (marker == Marker.SINGLE_GLOSSARY) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = markerDisplayLabels[marker] ?: marker,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                if (dictionaryNames.isNotEmpty()) {
                                    singleGlossaryExpanded = true
                                }
                            },
                            trailingIcon = {
                                if (dictionaryNames.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Outlined.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                        )

                        if (singleGlossaryExpanded) {
                            dictionaryNames.forEach { dictName ->
                                DropdownMenuItem(
                                    text = { Text(dictName, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        val markerStr = "{${Marker.SINGLE_GLOSSARY}-$dictName}"
                                        val normalizedValue = fieldValue.replace("<br>", "")
                                        val newValue = if (normalizedValue.contains(markerStr)) {
                                            normalizedValue.replace(markerStr, "")
                                        } else {
                                            normalizedValue + markerStr
                                        }
                                        onValueChange(newValue)
                                        singleGlossaryExpanded = false
                                        dropdownExpanded = false
                                    },
                                    leadingIcon = {
                                        val markerStr = "{${Marker.SINGLE_GLOSSARY}-$dictName}"
                                        androidx.compose.material3.Checkbox(
                                            checked = fieldValue.replace("<br>", "").contains(markerStr),
                                            onCheckedChange = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("All dictionaries", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    val markerStr = "{${Marker.SINGLE_GLOSSARY}-all}"
                                    val normalizedValue = fieldValue.replace("<br>", "")
                                    val newValue = if (normalizedValue.contains(markerStr)) {
                                        normalizedValue.replace(markerStr, "")
                                    } else {
                                        normalizedValue + markerStr
                                    }
                                    onValueChange(newValue)
                                    singleGlossaryExpanded = false
                                    dropdownExpanded = false
                                },
                                leadingIcon = {
                                    val markerStr = "{${Marker.SINGLE_GLOSSARY}-all}"
                                    androidx.compose.material3.Checkbox(
                                        checked = fieldValue.replace("<br>", "").contains(markerStr),
                                        onCheckedChange = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                        }
                    } else {
                        val isSelected = marker in currentMarkers
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = markerDisplayLabels[marker] ?: marker,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                val normalizedValue = fieldValue.replace("<br>", "")
                                val newValue = if (isSelected) {
                                    normalizedValue.replace("{$marker}", "")
                                } else {
                                    normalizedValue + "{$marker}"
                                }
                                onValueChange(newValue)
                                dropdownExpanded = false
                            },
                            leadingIcon = {
                                androidx.compose.material3.Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun parseMarkersForDisplay(fieldValue: String): List<String> {
        if (fieldValue.isBlank()) return emptyList()
        val normalized = fieldValue.replace("<br>", "")
        val markerRegex = Regex("""\{([a-zA-Z0-9-]+)\}""")
        return markerRegex.findAll(normalized).map { it.groupValues[1] }.toList()
    }

    private fun convertToStorageFormat(displayValue: String): String {
        if (displayValue.isBlank()) return ""
        val markers = parseMarkersForDisplay(displayValue)
        if (markers.isEmpty()) return displayValue
        return markers.joinToString("<br>") { "{$it}" }
    }
}

private suspend fun importDictionaryFromUri(context: Context, uri: Uri, currentOrder: String): Pair<String, Boolean> {
    return withContext(Dispatchers.IO) {
        val dictionariesDir = File(context.getExternalFilesDir(null), "dictionaries")
        Log.d(TAG, "importDictionaryFromUri: dictionariesDir=${dictionariesDir.absolutePath}")

        if (!dictionariesDir.exists() && !dictionariesDir.mkdirs()) {
            Log.e(TAG, "importDictionaryFromUri: failed to create dictionariesDir")
            return@withContext Pair(
                context.stringResource(MR.strings.storage_failed_to_create_directory, dictionariesDir.absolutePath),
                false,
            )
        }

        val tempZip = File(context.cacheDir, "chimahon_import_${System.currentTimeMillis()}.zip")
        Log.d(TAG, "importDictionaryFromUri: tempZip=${tempZip.absolutePath}")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempZip.outputStream().use { output ->
                    input.copyTo(output)
                    Log.d(TAG, "importDictionaryFromUri: copied zip to temp file, size=${tempZip.length()}")
                }
            } ?: return@withContext Pair(context.stringResource(MR.strings.file_null_uri_error), false)

            Log.d(TAG, "importDictionaryFromUri: calling HoshiDicts.importDictionary...")
            val result = HoshiDicts.importDictionary(
                zipPath = tempZip.absolutePath,
                outputDir = dictionariesDir.absolutePath,
            )
            Log.d(TAG, "importDictionaryFromUri: HoshiDicts result: success=${result.success} terms=${result.termCount} meta=${result.metaCount} media=${result.mediaCount}")

            if (!result.success) {
                Log.e(TAG, "importDictionaryFromUri: import failed")
                return@withContext Pair(context.stringResource(MR.strings.pref_import_dictionary_failed), false)
            }

            val newDictName = dictionariesDir.listFiles()
                ?.filter { it.isDirectory }
                ?.maxByOrNull { it.lastModified() }
                ?.name
            Log.d(TAG, "importDictionaryFromUri: newDictName=$newDictName")

            if (newDictName != null) {
                val orderList = currentOrder.split(",").filter { it.isNotBlank() }
                val newOrderList = orderList + newDictName
                Log.d(TAG, "importDictionaryFromUri: updating order to: $newOrderList")
                withContext(Dispatchers.Main) {
                    Injekt.get<DictionaryPreferences>().dictionaryOrder().set(newOrderList.joinToString(","))
                }
            }

            Pair(
                context.stringResource(
                    MR.strings.pref_import_dictionary_success,
                    result.termCount,
                    result.metaCount,
                    result.mediaCount,
                ),
                true,
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "importDictionaryFromUri: UnsatisfiedLinkError", e)
            Pair("Native library not loaded. Check build configuration.", false)
        } catch (e: Throwable) {
            Log.e(TAG, "importDictionaryFromUri: exception", e)
            Pair(e.message ?: context.stringResource(MR.strings.unknown_error), false)
        } finally {
            if (tempZip.exists()) {
                tempZip.delete()
                Log.d(TAG, "importDictionaryFromUri: deleted temp file")
            }
        }
    }
}
