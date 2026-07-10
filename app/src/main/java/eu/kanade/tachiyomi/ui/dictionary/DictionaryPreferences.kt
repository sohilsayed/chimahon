package eu.kanade.tachiyomi.ui.dictionary

import chimahon.anki.AnkiProfile
import chimahon.anki.AnkiProfileStore
import chimahon.audio.WordAudioPreferences
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class DictionaryPreferences(
    private val preferenceStore: PreferenceStore,
) : WordAudioPreferences {

    fun popupWidth() = preferenceStore.getInt("pref_dictionary_popup_width", 300)

    fun popupHeight() = preferenceStore.getInt("pref_dictionary_popup_height", 360)

    fun popupMode() = preferenceStore.getString("pref_dictionary_popup_mode", "floating")
    fun fontSize() = preferenceStore.getInt("pref_dictionary_font_size", 16)
    fun fontFamily() = preferenceStore.getString("pref_dictionary_font_family", "")

    fun ocrBoxScale() = preferenceStore.getFloat("pref_ocr_box_scale", 1.0f)

    fun ocrBoxScaleX() = preferenceStore.getFloat("pref_ocr_box_scale_x", ocrBoxScale().get())

    fun ocrBoxScaleY() = preferenceStore.getFloat("pref_ocr_box_scale_y", ocrBoxScale().get())

    fun ocrBoxOpacity() = preferenceStore.getFloat("pref_ocr_box_opacity", 0.0f)

    /** "cloud" (default) or "local" */
    fun ocrEngine() = preferenceStore.getString("pref_ocr_engine", "cloud")

    fun videoOcrSentenceAudioPaddingSeconds() = preferenceStore.getInt("pref_video_ocr_sentence_audio_padding_seconds", 3)

    fun showFrequencyHarmonic() = preferenceStore.getBoolean("pref_dict_show_frequency_harmonic", false)
    fun showFrequencyAverage() = preferenceStore.getBoolean("pref_dict_show_frequency_average", false)

    fun groupTerms() = preferenceStore.getBoolean("pref_dict_group_terms", true)
    fun showPitchDiagram() = preferenceStore.getBoolean("pref_dict_show_pitch_diagram", true)
    fun showPitchNumber() = preferenceStore.getBoolean("pref_dict_show_pitch_number", true)
    fun showPitchText() = preferenceStore.getBoolean("pref_dict_show_pitch_text", true)
    fun groupPitches() = preferenceStore.getBoolean("pref_dict_group_pitches", false)
    fun showNavigationButtons() = preferenceStore.getBoolean("pref_dict_show_navigation_buttons", true)

    fun autoKanaConversion() = preferenceStore.getBoolean("pref_dict_auto_kana_conversion", true)

    fun recursiveLookupMode() = preferenceStore.getString("pref_dict_recursive_lookup_mode", "tabs")

    /** Whether horizontal swipe-to-dismiss is enabled on dictionary popups. */
    fun popupSwipeToDismiss() = preferenceStore.getBoolean("pref_dictionary_popup_swipe_to_dismiss", true)

    /** Horizontal swipe distance (in dp) required to dismiss a dictionary popup. */
    fun popupSwipeThreshold() = preferenceStore.getInt("pref_dictionary_popup_swipe_threshold", 56)

    fun themeMode(): tachiyomi.core.common.preference.Preference<String> {
        // One-time migration from old boolean pref
        val oldPref = preferenceStore.getBoolean("pref_dictionary_theme_dark_amoled", false)
        if (oldPref.isSet()) {
            val oldValue = oldPref.get()
            oldPref.delete()
            val newPref = preferenceStore.getString("pref_dictionary_theme_mode", "system")
            newPref.set(if (oldValue) "pure_black" else "system")
            return newPref
        }
        return preferenceStore.getString("pref_dictionary_theme_mode", "system")
    }
    fun customColor() = preferenceStore.getInt("pref_dictionary_custom_color", 0)

    fun eInkMode() = preferenceStore.getBoolean("pref_dictionary_eink_mode", false)

    fun paginatedScrolling() = preferenceStore.getBoolean("pref_dictionary_paginated_scrolling", false)

    fun customCss() = preferenceStore.getString("pref_dictionary_custom_css", "")

    // -------------------------------------------------------------------------
    // Profile storage (raw pref keys — consumed by AnkiProfileStore and settings UI)
    // -------------------------------------------------------------------------

    fun rawProfiles() = preferenceStore.getString("pref_anki_profiles", "[]")

    fun rawActiveProfileId() = preferenceStore.getString("pref_active_profile_id", "")

    // -------------------------------------------------------------------------
    // AnkiProfileStore (single instance, lazy)
    // -------------------------------------------------------------------------

    val profileStore: AnkiProfileStore by lazy {
        AnkiProfileStore(
            readProfiles = { rawProfiles().get() },
            writeProfiles = { rawProfiles().set(it) },
            readActiveId = { rawActiveProfileId().get() },
            writeActiveId = { rawActiveProfileId().set(it) },
        ).apply {
            if (getProfiles().isEmpty()) {
                val legacyDicts = dictionaryOrder().get()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                migrateIfEmpty(
                    defaultName = "Default",
                    legacyValues = AnkiProfileStore.LegacyAnkiValues(
                        deck = legacyAnkiDeck().get(),
                        model = legacyAnkiModel().get(),
                        fieldMap = legacyAnkiFieldMap().get(),
                        tags = legacyAnkiDefaultTags().get(),
                        dupCheck = legacyAnkiDuplicateCheck().get(),
                        dupScope = legacyAnkiDuplicateScope().get(),
                        dupAction = legacyAnkiDuplicateAction().get(),
                        cropMode = legacyAnkiCropMode().get(),
                    ),
                    allDictNames = legacyDicts,
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-manga / per-source profile overrides
    // -------------------------------------------------------------------------

    /**
     * Returns a raw [tachiyomi.core.common.preference.Preference] for the given
     * dynamic override key.  Callers use [chimahon.dictionary.DictionaryProfileResolver.mangaOverrideKey]
     * or [chimahon.dictionary.DictionaryProfileResolver.sourceOverrideKey] to build the key.
     */
    fun rawProfileOverride(key: String) = preferenceStore.getString(key, "")

    /**
     * The single [chimahon.dictionary.DictionaryProfileResolver] instance.
     * Reads override keys directly from [preferenceStore].
     */
    val profileResolver: chimahon.dictionary.DictionaryProfileResolver by lazy {
        chimahon.dictionary.DictionaryProfileResolver(
            profileStore = profileStore,
            readMangaOverride = { mangaId ->
                preferenceStore.getString(
                    chimahon.dictionary.DictionaryProfileResolver.mangaOverrideKey(mangaId), "",
                ).get()
            },
            readSourceOverride = { sourceId ->
                preferenceStore.getString(
                    chimahon.dictionary.DictionaryProfileResolver.sourceOverrideKey(sourceId), "",
                ).get()
            },
            readNovelOverride = { novelId ->
                preferenceStore.getString(
                    chimahon.dictionary.DictionaryProfileResolver.novelOverrideKey(novelId), "",
                ).get()
            },
        )
    }

    /**
     * Delete a profile by ID and clean up any manga/source override keys that
     * pointed to it, so orphaned overrides can never resolve to a ghost profile.
     * Returns false (and does nothing) if it is the last profile.
     */
    fun deleteProfileWithOverrides(profileId: String): Boolean {
        val deleted = profileStore.deleteProfile(profileId)
        if (!deleted) return false

        // Sweep all pref keys — any override that matched the deleted ID becomes ""
        val mangaPrefix = "pref_dict_profile_manga_"
        val sourcePrefix = "pref_dict_profile_source_"
        val novelPrefix = "pref_dict_profile_novel_"
        preferenceStore.getAll().keys
            .filter { it.startsWith(mangaPrefix) || it.startsWith(sourcePrefix) || it.startsWith(novelPrefix) }
            .forEach { key ->
                val pref = preferenceStore.getString(key, "")
                if (pref.get() == profileId) pref.delete()
            }
        return true
    }

    // -------------------------------------------------------------------------
    // Legacy flat-key READERS — only used for one-time migration.
    // -------------------------------------------------------------------------

    fun legacyAnkiDeck() = preferenceStore.getString("pref_anki_deck", "")
    fun legacyAnkiModel() = preferenceStore.getString("pref_anki_model", "")
    fun legacyAnkiFieldMap() = preferenceStore.getString("pref_anki_field_map", "{}")
    fun legacyAnkiDuplicateCheck() = preferenceStore.getBoolean("pref_anki_duplicate_check", true)
    fun legacyAnkiDuplicateScope() = preferenceStore.getString("pref_anki_duplicate_scope", "deck")
    fun legacyAnkiDuplicateAction() = preferenceStore.getString("pref_anki_duplicate_action", "prevent")
    fun legacyAnkiDefaultTags() = preferenceStore.getString("pref_anki_default_tags", "chimahon")
    fun legacyAnkiCropMode() = preferenceStore.getString("pref_dict_anki_crop_mode", "full")

    // -------------------------------------------------------------------------
    // Dictionary display names (dirName → displayName, stored as JSON map)
    // -------------------------------------------------------------------------

    fun displayNames() = preferenceStore.getString("pref_display_names", "{}")

    /**
     * Get display name for a directory, or null if none is set.
     */
    fun getDisplayName(dirName: String): String? {
        val json = displayNames().get()
        if (json.isBlank() || json == "{}") return null
        return try {
            org.json.JSONObject(json).optString(dirName, null)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Set display name for a directory. Pass null to remove it.
     */
    fun setDisplayName(dirName: String, displayName: String?) {
        val json = displayNames().get()
        val obj = try {
            if (json.isBlank() || json == "{}") org.json.JSONObject()
            else org.json.JSONObject(json)
        } catch (_: Exception) {
            org.json.JSONObject()
        }
        if (displayName != null) {
            obj.put(dirName, displayName)
        } else {
            obj.remove(dirName)
        }
        displayNames().set(obj.toString())
    }

    /**
     * One-time cleanup: clear stale migration artifacts from a previous version.
     * Resets display names and removes the migration flag so it runs only once.
     */
    fun clearMigrationArtifacts() {
        val migrated = preferenceStore.getBoolean("pref_display_names_migrated", false).get()
        if (migrated) {
            displayNames().set("{}")
            preferenceStore.getBoolean("pref_display_names_migrated", false).set(false)
        }
    }

    /** Legacy global dictionary order — kept only to supply the initial migration list. */
    fun dictionaryOrder() = preferenceStore.getString("pref_dictionary_order", "")

    // -------------------------------------------------------------------------
    // Dictionary auto-update
    // -------------------------------------------------------------------------

    fun autoUpdateEnabled() = preferenceStore.getBoolean("pref_dict_auto_update", false)
    fun autoUpdateInterval() = preferenceStore.getInt("pref_dict_auto_update_interval", 24)
    fun lastDictUpdateCheck() = preferenceStore.getLong("pref_last_dict_update_check", 0L)
    fun dictUpdateCheckState() = preferenceStore.getString("pref_dict_update_check_state", "idle")

    // -------------------------------------------------------------------------
    // Word Audio Preferences (Implementing WordAudioPreferences interface)
    // -------------------------------------------------------------------------

    override fun wordAudioEnabled() = preferenceStore.getBoolean("pref_word_audio_enabled", true)
    override fun wordAudioAutoplay() = preferenceStore.getBoolean("pref_word_audio_autoplay", false)
    
    /** JSON list of WordAudioSource */
    override fun wordAudioSources() = preferenceStore.getString("pref_word_audio_sources", "[]")
    
    /** Local android.db file path */
    override fun wordAudioLocalPath() = preferenceStore.getString("pref_word_audio_local_path", "")

    /** SAF Uri string for the audio database */
    override fun wordAudioLocalUri() = preferenceStore.getString("pref_word_audio_local_uri", "")

    override fun wordAudioLocalEnabled() = preferenceStore.getBoolean("pref_word_audio_local_enabled", false)
}
