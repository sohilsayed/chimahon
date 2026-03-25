package eu.kanade.presentation.more.settings.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
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

private const val TAG = "DictionaryImport"

private val _dictionaryNames = MutableStateFlow<List<String>>(emptyList())
private val dictionaryNames = _dictionaryNames.asStateFlow()

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
                    subtitle = "${scale}%",
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
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            Log.d(TAG, "importLauncher: uri=${uri?.toString()}")
            if (uri == null) {
                context.toast(MR.strings.file_null_uri_error)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                Log.d(TAG, "importDictionaryFromUri: starting import...")
                val result = importDictionaryFromUri(context, uri, orderPref.get())
                Log.d(TAG, "importDictionaryFromUri: result=${result.first} success=${result.second}")

                context.toast(result.first)

                Log.d(TAG, "refreshing dictionary list after import")
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
}
