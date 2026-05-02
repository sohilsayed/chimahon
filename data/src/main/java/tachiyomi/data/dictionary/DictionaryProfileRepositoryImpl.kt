package tachiyomi.data.dictionary

import chimahon.dictionary.DictionaryProfile
import chimahon.dictionary.DictionaryProfileRepository
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler

class DictionaryProfileRepositoryImpl(
    private val handler: DatabaseHandler,
) : DictionaryProfileRepository {

    override fun getProfiles(): Flow<List<DictionaryProfile>> {
        return handler.subscribeToList {
            dictionary_profilesQueries.getProfiles(DictionaryMapper::mapProfile)
        }
    }

    override fun getProfileByLanguageFlow(languageCode: String): Flow<DictionaryProfile?> {
        return handler.subscribeToOneOrNull {
            dictionary_profilesQueries.getProfileByLanguage(languageCode, DictionaryMapper::mapProfile)
        }
    }

    override suspend fun getProfileById(id: String): DictionaryProfile? {
        return handler.awaitOneOrNull {
            dictionary_profilesQueries.getProfileById(id, DictionaryMapper::mapProfile)
        }
    }

    override suspend fun getProfileByManga(mangaId: Long): DictionaryProfile? {
        return handler.awaitOneOrNull {
            dictionary_profilesQueries.getProfileByManga(mangaId, DictionaryMapper::mapProfile)
        }
    }

    override suspend fun getProfileBySource(sourceId: Long): DictionaryProfile? {
        return handler.awaitOneOrNull {
            dictionary_profilesQueries.getProfileBySource(sourceId, DictionaryMapper::mapProfile)
        }
    }

    override suspend fun getProfileByLanguage(languageCode: String): DictionaryProfile? {
        return handler.awaitOneOrNull {
            dictionary_profilesQueries.getProfileByLanguage(languageCode, DictionaryMapper::mapProfile)
        }
    }

    override suspend fun upsertProfile(profile: DictionaryProfile) {
        handler.await {
            dictionary_profilesQueries.upsertProfile(
                id = profile.id,
                name = profile.name,
                language_code = profile.languageCode,
                anki_enabled = profile.ankiEnabled,
                anki_deck = profile.ankiDeck,
                anki_model = profile.ankiModel,
                anki_field_map = profile.ankiFieldMap,
                anki_tags = profile.ankiTags,
                anki_dup_check = profile.ankiDupCheck,
                anki_dup_scope = profile.ankiDupScope,
                anki_dup_action = profile.ankiDupAction,
                anki_crop_mode = profile.ankiCropMode,
                dictionary_order = profile.dictionaryOrder,
                enabled_dictionaries = profile.enabledDictionaries
            )
        }
    }

    override suspend fun deleteProfile(id: String) {
        handler.await {
            dictionary_profilesQueries.deleteProfile(id)
        }
    }

    override suspend fun setMangaProfile(mangaId: Long, profileId: String?) {
        handler.await {
            if (profileId == null) {
                dictionary_profilesQueries.deleteMangaProfile(mangaId)
            } else {
                dictionary_profilesQueries.setMangaProfile(mangaId, profileId)
            }
        }
    }

    override suspend fun setSourceProfile(sourceId: Long, profileId: String?) {
        handler.await {
            if (profileId == null) {
                dictionary_profilesQueries.deleteSourceProfile(sourceId)
            } else {
                dictionary_profilesQueries.setSourceProfile(sourceId, profileId)
            }
        }
    }

    override suspend fun setLanguageProfile(languageCode: String, profileId: String?) {
        handler.await {
            if (profileId == null) {
                dictionary_profilesQueries.deleteLanguageProfile(languageCode)
            } else {
                dictionary_profilesQueries.setLanguageProfile(languageCode, profileId)
            }
        }
    }
}
