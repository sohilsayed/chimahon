package chimahon.dictionary

import kotlinx.coroutines.flow.first
import tachiyomi.domain.source.repository.SourceRepository

class GetDictionaryProfile(
    private val dictionaryRepository: DictionaryProfileRepository,
) {
    suspend fun execute(mangaId: Long? = null, sourceId: Long? = null, lang: String? = null): DictionaryProfile {
        // 1. Entry (Manga) override
        if (mangaId != null && mangaId > 0) {
            dictionaryRepository.getProfileByManga(mangaId)?.let { return it }
        }
        
        // 2. Source (Extension) override
        if (sourceId != null && sourceId > 0) {
            dictionaryRepository.getProfileBySource(sourceId)?.let { return it }
        }
        
        // 3. Language default
        if (lang != null && lang.isNotBlank() && lang != "multi") {
            // Check direct mapping
            dictionaryRepository.getProfileByLanguage(lang)?.let { return it }
            
            // Fallback: select the first profile with that languageCode
            val profiles = dictionaryRepository.getProfiles().first()
            profiles.firstOrNull { it.languageCode == lang }?.let { return it }
        }
        
        // 4. Global / Fallback
        // Return the first profile or a default one
        val allProfiles = dictionaryRepository.getProfiles().first()
        return allProfiles.firstOrNull() ?: DictionaryProfile.default()
    }
}
