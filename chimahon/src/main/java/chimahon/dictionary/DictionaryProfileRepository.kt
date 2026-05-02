package chimahon.dictionary

import kotlinx.coroutines.flow.Flow

interface DictionaryProfileRepository {
    fun getProfiles(): Flow<List<DictionaryProfile>>
    fun getProfileByLanguageFlow(languageCode: String): Flow<DictionaryProfile?>
    suspend fun getProfileById(id: String): DictionaryProfile?
    suspend fun getProfileByManga(mangaId: Long): DictionaryProfile?
    suspend fun getProfileBySource(sourceId: Long): DictionaryProfile?
    suspend fun getProfileByLanguage(languageCode: String): DictionaryProfile?
    
    suspend fun upsertProfile(profile: DictionaryProfile)
    suspend fun deleteProfile(id: String)
    
    suspend fun setMangaProfile(mangaId: Long, profileId: String?)
    suspend fun setSourceProfile(sourceId: Long, profileId: String?)
    suspend fun setLanguageProfile(languageCode: String, profileId: String?)
}
