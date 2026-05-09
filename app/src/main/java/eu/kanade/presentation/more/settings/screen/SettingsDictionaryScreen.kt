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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import com.canopus.chimareader.data.FontManager
import chimahon.anki.AnkiCardCreator
import chimahon.anki.AnkiDroidBridge
import chimahon.anki.AnkiProfile
import chimahon.anki.Marker
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.screen.appearance.AppCustomThemeColorPickerScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import tachiyomi.i18n.kmk.KMR
import java.io.File
import java.util.Collections.emptyList

private const val TAG = "DictionaryImport"

private val _dictionaryNames = MutableStateFlow<List<String>>(emptyList())
private val dictionaryNames = _dictionaryNames.asStateFlow()

private data class MarkerSection(
    val title: String,
    val markers: List<String>,
)

private val markerSections = listOf(
    MarkerSection("Core", listOf(Marker.EXPRESSION, Marker.READING, Marker.FURIGANA, Marker.FURIGANA_PLAIN)),
    MarkerSection(
        "Glossary",
        listOf(
            Marker.GLOSSARY,
            Marker.GLOSSARY_BRIEF,
            Marker.GLOSSARY_PLAIN,
            Marker.GLOSSARY_PLAIN_NO_DICT,
            Marker.GLOSSARY_NO_DICT,
            Marker.GLOSSARY_FIRST,
            Marker.GLOSSARY_FIRST_NO_DICT,
            Marker.GLOSSARY_FIRST_BRIEF,
            Marker.SELECTED_GLOSSARY,
            Marker.SINGLE_GLOSSARY,
        ),
    ),
    MarkerSection(
        "Sentence",
        listOf(
            Marker.SENTENCE,
            Marker.SENTENCE_BOLD,
            Marker.CLOZE_PREFIX,
            Marker.CLOZE_BODY,
            Marker.CLOZE_BODY_KANA,
            Marker.CLOZE_SUFFIX,
            Marker.POPUP_SELECTION_TEXT,
        ),
    ),
    MarkerSection(
        "Pitch",
        listOf(
            Marker.PITCH_ACCENTS,
            Marker.PITCH_ACCENT_POSITIONS,
            Marker.PITCH_ACCENT_CATEGORIES,
            Marker.PITCH_ACCENT_GRAPHS,
            Marker.PITCH_ACCENT_GRAPHS_JJ,
            Marker.PITCH_ACCENT_COMPOSITE,
            Marker.MORAE,
        ),
    ),
    MarkerSection("Frequency", listOf(Marker.FREQUENCIES, Marker.FREQUENCY_LOWEST, Marker.FREQUENCY_HARMONIC_RANK, Marker.FREQUENCY_AVERAGE_RANK)),
    MarkerSection("Media", listOf(Marker.WORD_AUDIO, Marker.AUDIO, Marker.SCREENSHOT)),
    MarkerSection(
        "Context",
        listOf(
            Marker.TAGS,
            Marker.PART_OF_SPEECH,
            Marker.CONJUGATION,
            Marker.DICTIONARY,
            Marker.DICTIONARY_ALIAS,
            Marker.SEARCH_QUERY,
            Marker.URL,
            Marker.BOOK,
            Marker.CHAPTER,
            Marker.MEDIA,
            Marker.DOCUMENT_TITLE,
            Marker.SENTENCE_AUDIO,
        ),
    ),
)

private val markerDisplayLabels: Map<String, String> = Marker.ALL_WITH_TODO.associateWith { marker ->
    val isTodo = marker in Marker.TODO_MARKERS
    val prefix = if (isTodo) "" else ""
    when (marker) {
        Marker.EXPRESSION -> "${prefix}Expression"
        Marker.READING -> "${prefix}Reading"
        Marker.FURIGANA -> "${prefix}Furigana"
        Marker.FURIGANA_PLAIN -> "${prefix}Furigana Plain"
        Marker.AUDIO -> "${prefix}Audio"
        Marker.GLOSSARY -> "${prefix}Glossary"
        Marker.GLOSSARY_BRIEF -> "${prefix}Glossary Brief"
        Marker.GLOSSARY_PLAIN -> "${prefix}Glossary Plain"
        Marker.GLOSSARY_PLAIN_NO_DICT -> "${prefix}Glossary Plain No Dict"
        Marker.GLOSSARY_NO_DICT -> "${prefix}Glossary No Dict"
        Marker.GLOSSARY_FIRST -> "${prefix}Glossary First"
        Marker.GLOSSARY_FIRST_NO_DICT -> "${prefix}Glossary First No Dict"
        Marker.GLOSSARY_FIRST_BRIEF -> "${prefix}Glossary First Brief"
        Marker.SENTENCE -> "${prefix}Sentence"
        Marker.SENTENCE_BOLD -> "${prefix}Sentence Bold"
        Marker.CLOZE_PREFIX -> "${prefix}Cloze Prefix"
        Marker.CLOZE_BODY -> "${prefix}Cloze Body"
        Marker.CLOZE_BODY_KANA -> "${prefix}Cloze Body Kana"
        Marker.CLOZE_SUFFIX -> "${prefix}Cloze Suffix"
        Marker.TAGS -> "${prefix}Tags"
        Marker.CONJUGATION -> "${prefix}Conjugation"
        Marker.DICTIONARY -> "${prefix}Dictionary"
        Marker.DICTIONARY_ALIAS -> "${prefix}Dictionary Alias"
        Marker.FREQUENCIES -> "${prefix}Frequencies"
        Marker.FREQUENCY_LOWEST -> "${prefix}Freq Lowest"
        Marker.FREQUENCY_HARMONIC_RANK -> "${prefix}Freq Harmonic"
        Marker.FREQUENCY_AVERAGE_RANK -> "${prefix}Freq Average"
        Marker.PITCH_ACCENTS -> "${prefix}Pitch Accents"
        Marker.PITCH_ACCENT_POSITIONS -> "${prefix}Pitch Positions"
        Marker.PITCH_ACCENT_CATEGORIES -> "${prefix}Pitch Categories"
        Marker.PITCH_ACCENT_GRAPHS -> "${prefix}Pitch Graphs"
        Marker.PITCH_ACCENT_GRAPHS_JJ -> "${prefix}Pitch Graphs JJ"
        Marker.MORAE -> "${prefix}Morae"
        Marker.SCREENSHOT -> "${prefix}Screenshot"
        Marker.BOOK -> "${prefix}Book"
        Marker.CHAPTER -> "${prefix}Chapter"
        Marker.MEDIA -> "${prefix}Media"
        Marker.SINGLE_GLOSSARY -> "${prefix}Single Glossary ▸"
        Marker.PITCH_ACCENT_COMPOSITE -> "${prefix}Pitch Composite"
        Marker.SENTENCE_FURIGANA -> "${prefix}Sentence Furigana"
        Marker.SENTENCE_FURIGANA_PLAIN -> "${prefix}Sentence Furigana Plain"
        Marker.POPUP_SELECTION_TEXT -> "${prefix}Popup Selection"
        Marker.SELECTED_GLOSSARY -> "${prefix}Selected Glossary"
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

private val _isImporting = kotlinx.coroutines.flow.MutableStateFlow(false)

object SettingsDictionaryScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_dictionary

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }

        // Trigger recomposition when profile state changes
        val rawProfiles by dictionaryPreferences.rawProfiles().collectAsState()
        val rawActiveProfileId by dictionaryPreferences.rawActiveProfileId().collectAsState()

        LaunchedEffect(Unit) {
            loadDictionaryList(context)
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents(),
        ) { uris ->
            Log.d(TAG, "importLauncher: uris=${uris.size}")
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                _isImporting.value = true
                uris.forEach { uri ->
                    val activeProfile = dictionaryPreferences.profileStore.getActiveProfile()
                    val result = importDictionaryFromUri(context, uri, activeProfile)
                    context.toast(result.first)
                }
                loadDictionaryList(context)
                _isImporting.value = false
            }
        }

        val pickDb = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val targetFile = File(context.getExternalFilesDir(null), "word_audio.db")
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    val db = chimahon.audio.WordAudioDatabase(context)
                    if (db.updatePath(targetFile.absolutePath) && db.testConnection()) {
                        dictionaryPreferences.wordAudioLocalPath().set(targetFile.absolutePath)
                        context.toast("Local database loaded")
                    } else {
                        context.toast("Selected file is not a valid audio database or is corrupted")
                        if (targetFile.exists()) targetFile.delete()
                        dictionaryPreferences.wordAudioLocalPath().set("")
                    }
                    db.close()
                }
            }
        }

        return listOf(
            getAppearanceGroup(),
            getImportGroup(importLauncher),
            getAnkiProfileGroup(),
            getDictionaryListGroup(),
            getWordAudioGroup(pickDb),
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

        val fontSizePref = dictionaryPreferences.fontSize()
        val fontSize by fontSizePref.collectAsState()

        val ocrBoxScalePref = dictionaryPreferences.ocrBoxScale()
        val ocrBoxScale by ocrBoxScalePref.collectAsState()

        val amoledPref = dictionaryPreferences.themeDarkAmoled()
        val amoled by amoledPref.collectAsState()

        val navigator = LocalNavigator.currentOrThrow

        val showFreqHarmonicPref = dictionaryPreferences.showFrequencyHarmonic()
        val showFreqHarmonic by showFreqHarmonicPref.collectAsState()

        val groupTermsPref = dictionaryPreferences.groupTerms()
        val groupTerms by groupTermsPref.collectAsState()

        val customCssPref = dictionaryPreferences.customCss()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_dict_appearance),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = width,
                    title = stringResource(MR.strings.pref_dict_popup_width),
                    subtitle = "${width}px",
                    valueRange = 200..1920 step 10,
                    steps = 171,
                    onValueChanged = { newValue ->
                        widthPref.set(newValue)
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = height,
                    title = stringResource(MR.strings.pref_dict_popup_height),
                    subtitle = "${height}px",
                    valueRange = 100..1080 step 10,
                    steps = 97,
                    onValueChanged = { newValue ->
                        heightPref.set(newValue)
                    },
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Font Family",
                    content = {
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()
                        var importedFonts by remember { mutableStateOf(FontManager.getImportedFonts(context)) }
                        val allFonts = remember(importedFonts) { FontManager.defaultFonts + importedFonts }

                        val fontFamilyPref = dictionaryPreferences.fontFamily()
                        val selectedFont by fontFamilyPref.collectAsState()

                        var isImporting by remember { mutableStateOf(false) }
                        val fontPickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenDocument()
                        ) { uri: Uri? ->
                            uri?.let {
                                isImporting = true
                                scope.launch {
                                    val success = FontManager.importFont(context, it)
                                    if (success) {
                                        importedFonts = FontManager.getImportedFonts(context)
                                    }
                                    isImporting = false
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Font Family",
                                style = MaterialTheme.typography.titleMedium,
                            )

                            var fontExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = fontExpanded,
                                onExpandedChange = { fontExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedFont.takeIf { it.isNotBlank() } ?: "Default",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = fontExpanded,
                                    onDismissRequest = { fontExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Default") },
                                        onClick = {
                                            fontFamilyPref.set("")
                                            fontExpanded = false
                                        }
                                    )
                                    allFonts.forEach { font ->
                                        DropdownMenuItem(
                                            text = { Text(font) },
                                            onClick = {
                                                fontFamilyPref.set(font)
                                                fontExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        fontPickerLauncher.launch(
                                            arrayOf(
                                                "application/font-ttf",
                                                "application/x-font-ttf",
                                                "font/ttf",
                                                "application/x-font-otf",
                                                "font/otf",
                                                "application/octet-stream"
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isImporting
                                ) {
                                    if (isImporting) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Import Font")
                                    }
                                }

                                if (importedFonts.contains(selectedFont)) {
                                    OutlinedButton(
                                        onClick = {
                                            FontManager.deleteFont(context, selectedFont)
                                            importedFonts = FontManager.getImportedFonts(context)
                                            fontFamilyPref.set("")
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Delete Font")
                                    }
                                }
                            }
                        }
                    }
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = fontSize,
                    title = stringResource(MR.strings.pref_dict_popup_font_size),
                    subtitle = "${fontSize}px",
                    valueRange = 8..48,
                    steps = 40,
                    onValueChanged = { newValue ->
                        fontSizePref.set(newValue)
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (ocrBoxScale * 100).toInt(),
                    title = stringResource(MR.strings.pref_dict_ocr_box_scale),
                    subtitle = String.format("%.1fx", ocrBoxScale),
                    valueRange = 50..200 step 10,
                    steps = 14,
                    onValueChanged = { newValue ->
                        ocrBoxScalePref.set(newValue / 100f)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(KMR.strings.pref_custom_color),
                    subtitle = stringResource(KMR.strings.custom_color_description),
                    enabled = true,
                    onClick = {
                        navigator.push(AppCustomThemeColorPickerScreen(isDictionary = true))
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = amoledPref,
                    title = stringResource(MR.strings.pref_dark_theme_pure_black),
                    enabled = isSystemInDarkTheme(),
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
                Preference.PreferenceItem.SwitchPreference(
                    preference = dictionaryPreferences.showNavigationButtons(),
                    title = stringResource(KMR.strings.pref_dict_show_navigation_buttons),
                    subtitle = stringResource(KMR.strings.pref_dict_show_navigation_buttons_summary),
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_dict_custom_css),
                    content = {
                        var isDialogShown by remember { mutableStateOf(false) }
                        val css by customCssPref.collectAsState()

                        TextPreferenceWidget(
                            title = stringResource(MR.strings.pref_dict_custom_css),
                            subtitle = stringResource(MR.strings.pref_dict_custom_css_summary),
                            onPreferenceClick = { isDialogShown = true },
                        )

                        if (isDialogShown) {
                            var text by remember { mutableStateOf(css) }
                            AlertDialog(
                                onDismissRequest = { isDialogShown = false },
                                title = { Text(stringResource(MR.strings.pref_dict_custom_css)) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = stringResource(MR.strings.pref_dict_custom_css_summary),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        OutlinedTextField(
                                            value = text,
                                            onValueChange = { text = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(400.dp),
                                            placeholder = { Text("Paste your CSS here...") },
                                            singleLine = false,
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            customCssPref.set(text)
                                            isDialogShown = false
                                        },
                                    ) {
                                        Text(stringResource(MR.strings.action_ok))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { isDialogShown = false }) {
                                        Text(stringResource(MR.strings.action_cancel))
                                    }
                                },
                            )
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = dictionaryPreferences.recursiveLookupMode(),
                    entries = persistentListOf(
                        "tabs" to stringResource(MR.strings.pref_dict_recursive_mode_tabs),
                        "stack" to stringResource(MR.strings.pref_dict_recursive_mode_back),
                    ).associate { it.first to it.second }.toPersistentMap(),
                    title = stringResource(MR.strings.pref_dict_recursive_mode),
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_dict_pitch_accent_display),
                    content = {
                        val showPitchDiagramPref = dictionaryPreferences.showPitchDiagram()
                        val showPitchDiagram by showPitchDiagramPref.collectAsState()

                        val showPitchNumberPref = dictionaryPreferences.showPitchNumber()
                        val showPitchNumber by showPitchNumberPref.collectAsState()

                        val showPitchTextPref = dictionaryPreferences.showPitchText()
                        val showPitchText by showPitchTextPref.collectAsState()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(MR.strings.pref_dict_pitch_accent_display),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = showPitchDiagram,
                                    onClick = { showPitchDiagramPref.set(!showPitchDiagram) },
                                    label = { Text(stringResource(MR.strings.pref_dict_pitch_diagram)) },
                                )
                                FilterChip(
                                    selected = showPitchNumber,
                                    onClick = { showPitchNumberPref.set(!showPitchNumber) },
                                    label = { Text(stringResource(MR.strings.pref_dict_pitch_number)) },
                                )
                                FilterChip(
                                    selected = showPitchText,
                                    onClick = { showPitchTextPref.set(!showPitchText) },
                                    label = { Text(stringResource(MR.strings.pref_dict_pitch_text)) },
                                )
                            }
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getImportGroup(
        importLauncher: androidx.activity.result.ActivityResultLauncher<String>
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
        val rawProfiles by dictionaryPreferences.rawProfiles().collectAsState()
        val rawActiveProfileId by dictionaryPreferences.rawActiveProfileId().collectAsState()
        val profileStore = dictionaryPreferences.profileStore
        val activeProfile = remember(rawProfiles, rawActiveProfileId) { profileStore.getActiveProfile() }


        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_import_dictionary),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_import_dictionary),
                    content = {
                        val isImporting by _isImporting.collectAsState()
                        if (isImporting) {
                            AlertDialog(
                                onDismissRequest = {},
                                title = { Text(stringResource(MR.strings.pref_import_dictionary)) },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text("Importing dictionary... Please wait.")
                                    }
                                },
                                confirmButton = {}
                            )
                        }

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
    private fun getAnkiProfileGroup(): Preference.PreferenceGroup {
        val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
        val rawProfiles by dictionaryPreferences.rawProfiles().collectAsState()
        val rawActiveProfileId by dictionaryPreferences.rawActiveProfileId().collectAsState()
        val profileStore = dictionaryPreferences.profileStore

        val profiles = remember(rawProfiles) { profileStore.getProfiles() }
        val activeProfile = remember(profiles, rawActiveProfileId) { profileStore.getActiveProfile() }

        var showNewDialog by remember { mutableStateOf(false) }
        var showRenameDialog by remember { mutableStateOf<chimahon.anki.AnkiProfile?>(null) }
        var showDeleteDialog by remember { mutableStateOf<chimahon.anki.AnkiProfile?>(null) }

        if (showNewDialog) {
            var newName by remember { mutableStateOf("") }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showNewDialog = false },
                title = { Text(stringResource(MR.strings.pref_anki_profile_new)) },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(MR.strings.pref_anki_profile_name_hint)) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                val newProfile = activeProfile.copy(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = newName.trim()
                                )
                                profileStore.addProfile(newProfile)
                                showNewDialog = false
                            }
                        }
                    ) { Text(stringResource(MR.strings.pref_anki_profile_clone)) }
                },
                dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("Cancel") } }
            )
        }

        showRenameDialog?.let { profile ->
            var newName by remember { mutableStateOf(profile.name) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showRenameDialog = null },
                title = { Text(stringResource(MR.strings.pref_anki_profile_rename)) },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(MR.strings.pref_anki_profile_name_hint)) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                profileStore.updateProfile(profile.copy(name = newName.trim()))
                                showRenameDialog = null
                            }
                        }
                    ) { Text("Rename") }
                },
                dismissButton = { TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") } }
            )
        }

        showDeleteDialog?.let { profile ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text(stringResource(MR.strings.pref_anki_profile_delete)) },
                text = { Text(stringResource(MR.strings.pref_anki_profile_delete_confirm, profile.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            profileStore.deleteProfile(profile.id)
                            showDeleteDialog = null
                        }
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") } }
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_anki_profiles),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_anki_profiles),
                    content = {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(MR.strings.pref_anki_profile_active, activeProfile.name),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { showRenameDialog = activeProfile }) {
                                    Icon(imageVector = Icons.Outlined.Edit, contentDescription = "Rename")
                                }
                                IconButton(
                                    onClick = { showDeleteDialog = activeProfile },
                                    enabled = profiles.size > 1
                                ) {
                                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete")
                                }
                            }
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                profiles.forEach { profile ->
                                    item {
                                        androidx.compose.material3.FilterChip(
                                            selected = profile.id == activeProfile.id,
                                            onClick = { profileStore.setActiveProfile(profile.id) },
                                            label = { Text(profile.name) },
                                        )
                                    }
                                }
                                item {
                                    androidx.compose.material3.FilterChip(
                                        selected = false,
                                        onClick = { showNewDialog = true },
                                        label = { Text("+") },
                                    )
                                }
                            }

                            // Language selector
                            var langExpanded by remember { mutableStateOf(false) }
                            val languages = listOf(
                                "" to "Any (All)",
                                "ja" to "Japanese",
                                "ko" to "Korean",
                                "ar" to "Arabic",
                                "zh" to "Chinese",
                                "en" to "English",
                                "de" to "German",
                                "fr" to "French",
                                "ru" to "Russian",
                                "es" to "Spanish",
                                "it" to "Italian",
                            )
                            val currentLangName = languages.find { it.first == activeProfile.languageCode }?.second ?: activeProfile.languageCode

                            Box(modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedButton(
                                    onClick = { langExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Language: $currentLangName")
                                    Icon(Icons.Outlined.KeyboardArrowDown, null)
                                }
                                DropdownMenu(
                                    expanded = langExpanded,
                                    onDismissRequest = { langExpanded = false }
                                ) {
                                    languages.forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                profileStore.updateProfile(activeProfile.copy(languageCode = code))
                                                langExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            )
        )
    }

    @Composable
    private fun getDictionaryListGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }

        val rawProfiles by dictionaryPreferences.rawProfiles().collectAsState()
        val rawActiveProfileId by dictionaryPreferences.rawActiveProfileId().collectAsState()
        val profileStore = dictionaryPreferences.profileStore

        val activeProfile = remember(rawProfiles, rawActiveProfileId) { profileStore.getActiveProfile() }
        val currentOrder = activeProfile.dictionaryOrder
        val enabledDicts = activeProfile.enabledDictionaries
        val collapseMode = activeProfile.dictionaryCollapseMode
        val dictionaryDisplayModes = activeProfile.dictionaryDisplayModes

        val pickDictionary = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val (message, success) = importDictionaryFromUri(context, uri, activeProfile)
                    context.toast(message)
                    if (success) {
                        loadDictionaryList(context)
                    }
                }
            }
        }

        var dictToDelete by remember { mutableStateOf<String?>(null) }

        val dictionaries by dictionaryNames.collectAsState()

        val orderedDicts = remember(dictionaries, currentOrder) {
            val ordered = currentOrder.filter { it in dictionaries }
            val remaining = dictionaries.filter { it !in currentOrder }
            ordered + remaining
        }
        val collapseModeOptions = listOf(
            AnkiProfile.DICTIONARY_COLLAPSE_EXPAND_ALL to "Expand all dictionaries",
            AnkiProfile.DICTIONARY_COLLAPSE_EXPAND_FIRST_AVAILABLE to "Expand first available dictionary",
            AnkiProfile.DICTIONARY_COLLAPSE_COLLAPSE_ALL to "Collapse all dictionaries",
            AnkiProfile.DICTIONARY_COLLAPSE_CUSTOM to "Custom",
        )
        val customModeOptions = listOf(
            AnkiProfile.DICTIONARY_DISPLAY_ALWAYS_EXPANDED to "Always expanded",
            AnkiProfile.DICTIONARY_DISPLAY_FALLBACK to "Fallback",
            AnkiProfile.DICTIONARY_DISPLAY_ALWAYS_COLLAPSED to "Always collapsed",
        )
        val collapseModeLabel = collapseModeOptions.firstOrNull { it.first == collapseMode }?.second
            ?: collapseModeOptions.first().second
        fun customModeLabel(mode: String): String =
            customModeOptions.firstOrNull { it.first == mode }?.second
                ?: customModeOptions[1].second

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
                            val newOrder = orderedDicts.filter { d -> d != dictName }
                            val newEnabled = enabledDicts - dictName
                            val newDisplayModes = dictionaryDisplayModes - dictName
                            profileStore.updateProfile(
                                activeProfile.copy(
                                    dictionaryOrder = newOrder,
                                    enabledDictionaries = newEnabled,
                                    dictionaryDisplayModes = newDisplayModes,
                                ),
                            )
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
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            text = "Collapse behavior",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = "Controls which dictionary groups open first in lookup results.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )

                                        var collapseMenuExpanded by remember { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(
                                                onClick = { collapseMenuExpanded = true },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(
                                                    text = collapseModeLabel,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Icon(
                                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = collapseMenuExpanded,
                                                onDismissRequest = { collapseMenuExpanded = false },
                                            ) {
                                                collapseModeOptions.forEach { (mode, label) ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            profileStore.updateProfile(
                                                                activeProfile.copy(dictionaryCollapseMode = mode),
                                                            )
                                                            collapseMenuExpanded = false
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                orderedDicts.forEachIndexed { index, dictName ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        tonalElevation = 1.dp,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Text(
                                                    text = dictName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                if (collapseMode == AnkiProfile.DICTIONARY_COLLAPSE_CUSTOM) {
                                                    var dictModeExpanded by remember(dictName) { mutableStateOf(false) }
                                                    val selectedMode = dictionaryDisplayModes[dictName]
                                                        ?: AnkiProfile.DICTIONARY_DISPLAY_FALLBACK
                                                    Box {
                                                        OutlinedButton(
                                                            onClick = { dictModeExpanded = true },
                                                            modifier = Modifier.fillMaxWidth(),
                                                        ) {
                                                            Text(
                                                                text = customModeLabel(selectedMode),
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                            Icon(
                                                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp),
                                                            )
                                                        }
                                                        DropdownMenu(
                                                            expanded = dictModeExpanded,
                                                            onDismissRequest = { dictModeExpanded = false },
                                                        ) {
                                                            customModeOptions.forEach { (mode, label) ->
                                                                DropdownMenuItem(
                                                                    text = { Text(label) },
                                                                    onClick = {
                                                                        profileStore.updateProfile(
                                                                            activeProfile.copy(
                                                                                dictionaryDisplayModes = dictionaryDisplayModes + (dictName to mode),
                                                                            ),
                                                                        )
                                                                        dictModeExpanded = false
                                                                    },
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                            IconButton(
                                                onClick = {
                                                    val newEnabled = if (enabledDicts.isEmpty()) {
                                                        orderedDicts.filter { it != dictName }.toSet()
                                                    } else if (dictName in enabledDicts) {
                                                        enabledDicts - dictName
                                                    } else {
                                                        enabledDicts + dictName
                                                    }
                                                    profileStore.updateProfile(activeProfile.copy(enabledDictionaries = newEnabled))
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                val isEnabled = enabledDicts.isEmpty() || dictName in enabledDicts
                                                Icon(
                                                    imageVector = if (isEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                                    contentDescription = "Toggle visibility",
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val newList = orderedDicts.toMutableList()
                                                        val temp = newList[index]
                                                        newList[index] = newList[index - 1]
                                                        newList[index - 1] = temp
                                                        profileStore.updateProfile(activeProfile.copy(dictionaryOrder = newList))
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
                                                        profileStore.updateProfile(activeProfile.copy(dictionaryOrder = newList))
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
                                OutlinedButton(
                                    onClick = { pickDictionary.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(MR.strings.pref_import_dictionary))
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

        val dictionaries by dictionaryNames.collectAsState()

        val rawProfiles by dictionaryPreferences.rawProfiles().collectAsState()
        val rawActiveProfileId by dictionaryPreferences.rawActiveProfileId().collectAsState()
        val profileStore = dictionaryPreferences.profileStore
        val activeProfile = remember(rawProfiles, rawActiveProfileId) { profileStore.getActiveProfile() }

        val updateProfile: (chimahon.anki.AnkiProfile.() -> chimahon.anki.AnkiProfile) -> Unit = { transform ->
            profileStore.updateProfile(profileStore.getActiveProfile().transform())
        }

        var pendingPermissionCheck by remember { mutableStateOf(false) }

        // Check permission result when user returns from the system permission dialog
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && pendingPermissionCheck) {
                    pendingPermissionCheck = false
                    if (bridge.hasPermission()) {
                        updateProfile { copy(ankiEnabled = true) }
                    } else {
                        context.toast(MR.strings.pref_anki_permission_denied)
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val selectedDeck = activeProfile.ankiDeck
        val selectedModel = activeProfile.ankiModel
        val fieldMapJson = activeProfile.ankiFieldMap
        val dupCheck = activeProfile.ankiDupCheck
        val dupScope = activeProfile.ankiDupScope
        val dupAction = activeProfile.ankiDupAction
        val tags = activeProfile.ankiTags
        val cropMode = activeProfile.ankiCropMode
        val enabled = activeProfile.ankiEnabled

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
                    updated[normalizedName] = effectiveDisplayValue
                }
                updateProfile { copy(ankiFieldMap = org.json.JSONObject(updated).toString()) }
            }
        }

        val removeCustomField: (String) -> Unit = { fieldName ->
            val updated = fieldMap.toMutableMap()
            updated.remove(fieldName)
            updateProfile { copy(ankiFieldMap = org.json.JSONObject(updated).toString()) }
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
                        profileStore.updateProfile(profileStore.getActiveProfile().copy(ankiFieldMap = org.json.JSONObject(detectedMap).toString()))
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
                                            updateProfile { copy(ankiEnabled = false) }
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
                                            updateProfile { copy(ankiEnabled = true) }
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
                                onValueChange = { updateProfile { copy(ankiDeck = it) } },
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
                                    updateProfile { copy(ankiModel = it, ankiFieldMap = "{}") }
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
                                    onCheckedChange = { updateProfile { copy(ankiDupCheck = it) } },
                                )
                            }

                            if (dupCheck) {
                                AnkiDropdownPreference(
                                    label = stringResource(MR.strings.pref_anki_duplicate_scope),
                                    value = if (dupScope == "all") "Everywhere" else "Deck only",
                                    options = listOf("deck", "all"),
                                    displayOptions = listOf("Deck only", "Everywhere"),
                                    onValueChange = { updateProfile { copy(ankiDupScope = it) } },
                                )
                                AnkiDropdownPreference(
                                    label = stringResource(MR.strings.pref_anki_duplicate_action),
                                    value = when (dupAction) {
                                        "add" -> stringResource(MR.strings.pref_anki_duplicate_add)
                                        "overwrite" -> stringResource(MR.strings.pref_anki_duplicate_overwrite)
                                        else -> stringResource(MR.strings.pref_anki_duplicate_prevent)
                                    },
                                    options = listOf("prevent", "add", "overwrite"),
                                    displayOptions = listOf(
                                        stringResource(MR.strings.pref_anki_duplicate_prevent),
                                        stringResource(MR.strings.pref_anki_duplicate_add),
                                        stringResource(MR.strings.pref_anki_duplicate_overwrite),
                                    ),
                                    onValueChange = { updateProfile { copy(ankiDupAction = it) } },
                                )
                            }

                            // Default tags
                            OutlinedTextField(
                                value = tags,
                                onValueChange = { updateProfile { copy(ankiTags = it) } },
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
                                                updateProfile { copy(ankiCropMode = value) }
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
        var markerPickerOpen by remember { mutableStateOf(false) }

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
                    singleLine = false,
                    maxLines = 5,
                )

                IconButton(
                    onClick = { markerPickerOpen = true },
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

            if (markerPickerOpen) {
                MarkerPickerDialog(
                    fieldName = fieldName,
                    fieldValue = fieldValue,
                    dictionaryNames = dictionaryNames,
                    onDismiss = { markerPickerOpen = false },
                    onToggleMarker = { markerText ->
                        onValueChange(toggleMarkerText(fieldValue, markerText))
                    },
                )
            }
        }
    }

    @Composable
    private fun MarkerPickerDialog(
        fieldName: String,
        fieldValue: String,
        dictionaryNames: List<String>,
        onDismiss: () -> Unit,
        onToggleMarker: (String) -> Unit,
    ) {
        var query by remember { mutableStateOf("") }
        var selectedSection by remember { mutableStateOf<String?>(null) }
        var singleGlossaryExpanded by remember { mutableStateOf(false) }
        val currentMarkers = remember(fieldValue) { parseMarkersForDisplay(fieldValue).toSet() }
        val normalizedQuery = query.trim().lowercase()
        val visibleSections = remember(selectedSection, normalizedQuery) {
            if (normalizedQuery.isBlank()) {
                markerSections.filter { selectedSection == null || it.title == selectedSection }
            } else {
                markerSections.mapNotNull { section ->
                    val markers = section.markers.filter { marker ->
                        marker.contains(normalizedQuery, ignoreCase = true) ||
                            (markerDisplayLabels[marker] ?: marker).contains(normalizedQuery, ignoreCase = true)
                    }
                    if (markers.isEmpty()) null else section.copy(markers = markers)
                }
            }
        }
        val visibleDictionaryNames = remember(dictionaryNames, normalizedQuery) {
            if (normalizedQuery.isBlank()) {
                dictionaryNames
            } else {
                dictionaryNames.filter { it.contains(normalizedQuery, ignoreCase = true) }
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Markers", style = MaterialTheme.typography.titleLarge)
                    Text(fieldName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search markers") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedSection == null,
                            onClick = { selectedSection = null },
                            label = { Text("All") },
                        )
                        markerSections.forEach { section ->
                            FilterChip(
                                selected = selectedSection == section.title,
                                onClick = { selectedSection = section.title },
                                label = { Text(section.title) },
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        visibleSections.forEach { section ->
                            item("section-${section.title}") {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                )
                            }
                            items(section.markers, key = { marker -> "marker-$marker" }) { marker ->
                                if (marker == Marker.SINGLE_GLOSSARY) {
                                    MarkerPickerRow(
                                        label = markerDisplayLabels[marker] ?: marker,
                                        markerText = "{${Marker.SINGLE_GLOSSARY}-all}",
                                        selected = fieldValue.contains("{${Marker.SINGLE_GLOSSARY}-"),
                                        onClick = {
                                            singleGlossaryExpanded = !singleGlossaryExpanded
                                        },
                                    )
                                    if (singleGlossaryExpanded || normalizedQuery.isNotBlank()) {
                                        MarkerPickerRow(
                                            label = "All dictionaries",
                                            markerText = "{${Marker.SINGLE_GLOSSARY}-all}",
                                            selected = fieldValue.contains("{${Marker.SINGLE_GLOSSARY}-all}"),
                                            onClick = { onToggleMarker("{${Marker.SINGLE_GLOSSARY}-all}") },
                                            compact = true,
                                        )
                                        visibleDictionaryNames.forEach { dictName ->
                                            val markerText = "{${Marker.SINGLE_GLOSSARY}-$dictName}"
                                            MarkerPickerRow(
                                                label = dictName,
                                                markerText = markerText,
                                                selected = fieldValue.contains(markerText),
                                                onClick = { onToggleMarker(markerText) },
                                                compact = true,
                                            )
                                        }
                                    }
                                } else {
                                    val markerText = "{$marker}"
                                    MarkerPickerRow(
                                        label = markerDisplayLabels[marker] ?: marker,
                                        markerText = markerText,
                                        selected = marker in currentMarkers,
                                        onClick = { onToggleMarker(markerText) },
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            },
        )
    }

    @Composable
    private fun MarkerPickerRow(
        label: String,
        markerText: String,
        selected: Boolean,
        onClick: () -> Unit,
        compact: Boolean = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 18.dp else 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    markerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    private fun toggleMarkerText(fieldValue: String, markerText: String): String {
        val base = if (fieldValue == "{}") "" else fieldValue
        return if (base.contains(markerText)) {
            base.replace(markerText, "")
        } else {
            base + markerText
        }.ifBlank { "{}" }
    }

    @Composable
    private fun getWordAudioGroup(pickDb: androidx.activity.result.ActivityResultLauncher<Array<String>>): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val prefs = remember { Injekt.get<DictionaryPreferences>() }
        val json = remember { Injekt.get<kotlinx.serialization.json.Json>() }
        
        val enabled by prefs.wordAudioEnabled().collectAsState()
        val autoplay by prefs.wordAudioAutoplay().collectAsState()
        val localEnabled by prefs.wordAudioLocalEnabled().collectAsState()
        val localPath by prefs.wordAudioLocalPath().collectAsState()
        val rawSources by prefs.wordAudioSources().collectAsState()
        
        val sources = remember(rawSources) {
            try {
                json.decodeFromString<List<chimahon.audio.WordAudioSource>>(rawSources)
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        val updateSources: (List<chimahon.audio.WordAudioSource>) -> Unit = { newSources ->
            prefs.wordAudioSources().set(json.encodeToString(newSources))
        }


        return Preference.PreferenceGroup(
            title = "Word Audio",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.wordAudioEnabled(),
                    title = "Enable Word Audio",
                    subtitle = "Show audio buttons in dictionary entries",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.wordAudioAutoplay(),
                    title = "Autoplay",
                    subtitle = "Automatically play the first audio found",
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Local Audio",
                    content = {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Local Audio (android.db)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Switch(checked = localEnabled, onCheckedChange = { prefs.wordAudioLocalEnabled().set(it) })
                            }
                            if (localEnabled) {
                                Text(
                                    text = if (localPath.isNotBlank()) "Path: $localPath" else "No database selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (localPath.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { 
                                            try {
                                                pickDb.launch(arrayOf("*/*")) 
                                            } catch (e: Exception) {
                                                context.toast("Error launching file picker")
                                                Log.e(TAG, "pickDb launch error", e)
                                            }
                                        }
                                    ) {
                                        Text("Select Database")
                                    }

                                    if (localPath.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                val file = java.io.File(localPath)
                                                if (file.exists()) file.delete()
                                                prefs.wordAudioLocalPath().set("")
                                            }
                                        ) {
                                            Text("Delete Database")
                                        }
                                    }
                                }
                            }
                        }
                    }
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Online Sources",
                    content = {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("Online Sources", style = MaterialTheme.typography.titleMedium)
                            
                            // Sources List
                            sources.forEachIndexed { index, source ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(source.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(source.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    }
                                    Checkbox(
                                        checked = source.isEnabled,
                                        onCheckedChange = { checked ->
                                            val newSources = sources.toMutableList()
                                            newSources[index] = source.copy(isEnabled = checked)
                                            updateSources(newSources)
                                        }
                                    )
                                    IconButton(onClick = {
                                        val newSources = sources.toMutableList()
                                        newSources.removeAt(index)
                                        updateSources(newSources)
                                    }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            
                            // Add Source UI
                            var newName by remember { mutableStateOf("") }
                            var newUrl by remember { mutableStateOf("") }
                            
                            Column(modifier = Modifier.padding(top = 16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)) {
                                Text("Add Source", style = MaterialTheme.typography.labelLarge)
                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    label = { Text("Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    OutlinedTextField(
                                        value = newUrl,
                                        onValueChange = { newUrl = it },
                                        label = { Text("URL Template ({term}, {reading})") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    IconButton(
                                        onClick = {
                                            if (newName.isNotBlank() && newUrl.isNotBlank()) {
                                                val newSource = chimahon.audio.WordAudioSource(name = newName, url = newUrl)
                                                updateSources(sources + newSource)
                                                newName = ""
                                                newUrl = ""
                                            }
                                        },
                                        enabled = newName.isNotBlank() && newUrl.isNotBlank()
                                    ) {
                                        Icon(Icons.Outlined.Add, contentDescription = "Add")
                                    }
                                }
                            }
                        }
                    }
                )
            )
        )
    }

    private fun parseMarkersForDisplay(fieldValue: String): List<String> {
        if (fieldValue.isBlank()) return emptyList()
        val markerRegex = Regex("""\{([a-zA-Z0-9-]+)\}""")
        return markerRegex.findAll(fieldValue).map { it.groupValues[1] }.toList()
    }
}

private suspend fun importDictionaryFromUri(
    context: Context,
    uri: Uri,
    activeProfile: chimahon.anki.AnkiProfile,
): Pair<String, Boolean> {
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
                val orderList = activeProfile.dictionaryOrder.filter { it.isNotBlank() }
                if (newDictName !in orderList) {
                    val newOrderList = orderList + newDictName
                    Log.d(TAG, "importDictionaryFromUri: updating profile order to: $newOrderList")
                    val prefs = Injekt.get<DictionaryPreferences>()
                    prefs.profileStore.updateProfile(activeProfile.copy(dictionaryOrder = newOrderList))
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
