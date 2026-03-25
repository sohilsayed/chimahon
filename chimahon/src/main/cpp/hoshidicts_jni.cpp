#include <jni.h>

#include <memory>
#include <string>
#include <vector>

#include "hoshidicts/include/hoshidicts.h"

namespace {
    struct LookupObject {
        DictionaryQuery query;
        Deconjugator deconjugator;
        std::unique_ptr<Lookup> lookup;

        LookupObject() : lookup(std::make_unique<Lookup>(query, deconjugator)) {}
    };

    LookupObject *as_object(jlong handle) { return reinterpret_cast<LookupObject *>(handle); }

    std::string jstring_to_std_string(JNIEnv *env, jstring input) {
        const char *chars = env->GetStringUTFChars(input, nullptr);
        std::string output(chars);
        env->ReleaseStringUTFChars(input, chars);
        return output;
    }

    template<typename Fn>
    void for_each_string(JNIEnv *env, jobjectArray arr, Fn fn) {
        const jsize count = env->GetArrayLength(arr);
        for (jsize i = 0; i < count; ++i) {
            auto element = reinterpret_cast<jstring>(env->GetObjectArrayElement(arr, i));
            fn(jstring_to_std_string(env, element));
            env->DeleteLocalRef(element);
        }
    }

    jstring new_string(JNIEnv *env, const std::string &value) {
        return env->NewStringUTF(value.c_str());
    }

    jobject new_import_result(JNIEnv *env, bool success, jlong term_count, jlong meta_count,
                              jlong media_count) {
        jclass cls = env->FindClass("chimahon/ImportResult");
        jmethodID ctor = env->GetMethodID(cls, "<init>", "(ZJJJ)V");
        return env->NewObject(cls, ctor, static_cast<jboolean>(success), term_count, meta_count,
                              media_count);
    }

    jobjectArray
    new_process_array(JNIEnv *env, const std::vector<std::string> &process) {
        jclass cls = env->FindClass("java/lang/String");
        jobjectArray array = env->NewObjectArray(static_cast<jsize>(process.size()), cls, nullptr);
        for (size_t i = 0; i < process.size(); ++i) {
            jobject item = new_string(env, process[i]);
            env->SetObjectArrayElement(array, static_cast<jsize>(i), item);
            env->DeleteLocalRef(item);
        }
        return array;
    }

    jobject new_glossary_entry(JNIEnv *env, const GlossaryEntry &entry) {
        jclass cls = env->FindClass("chimahon/GlossaryEntry");
        jmethodID ctor =
                env->GetMethodID(cls, "<init>",
                                 "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        jstring dict_name = new_string(env, entry.dict_name);
        jstring glossary = new_string(env, entry.glossary);
        jstring definition_tags = new_string(env, entry.definition_tags);
        jstring term_tags = new_string(env, entry.term_tags);
        jobject out = env->NewObject(cls, ctor, dict_name, glossary, definition_tags, term_tags);
        env->DeleteLocalRef(dict_name);
        env->DeleteLocalRef(glossary);
        env->DeleteLocalRef(definition_tags);
        env->DeleteLocalRef(term_tags);
        return out;
    }

    jobjectArray new_glossary_entry_array(JNIEnv *env, const std::vector<GlossaryEntry> &entries) {
        jclass cls = env->FindClass("chimahon/GlossaryEntry");
        jobjectArray array = env->NewObjectArray(static_cast<jsize>(entries.size()), cls, nullptr);
        for (size_t i = 0; i < entries.size(); ++i) {
            jobject item = new_glossary_entry(env, entries[i]);
            env->SetObjectArrayElement(array, static_cast<jsize>(i), item);
            env->DeleteLocalRef(item);
        }
        return array;
    }

    jobject new_frequency(JNIEnv *env, const Frequency &frequency) {
        jclass cls = env->FindClass("chimahon/Frequency");
        jmethodID ctor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;)V");
        jstring display_value = new_string(env, frequency.display_value);
        jobject out = env->NewObject(cls, ctor, static_cast<jint>(frequency.value), display_value);
        env->DeleteLocalRef(display_value);
        return out;
    }

    jobjectArray new_frequency_array(JNIEnv *env, const std::vector<Frequency> &frequencies) {
        jclass cls = env->FindClass("chimahon/Frequency");
        jobjectArray array = env->NewObjectArray(static_cast<jsize>(frequencies.size()), cls,
                                                 nullptr);
        for (size_t i = 0; i < frequencies.size(); ++i) {
            jobject item = new_frequency(env, frequencies[i]);
            env->SetObjectArrayElement(array, static_cast<jsize>(i), item);
            env->DeleteLocalRef(item);
        }
        return array;
    }

    jobject new_frequency_entry(JNIEnv *env, const FrequencyEntry &entry) {
        jclass cls = env->FindClass("chimahon/FrequencyEntry");
        jmethodID ctor = env->GetMethodID(cls, "<init>",
                                          "(Ljava/lang/String;[Lchimahon/Frequency;)V");
        jstring dict_name = new_string(env, entry.dict_name);
        jobjectArray frequencies = new_frequency_array(env, entry.frequencies);
        jobject out = env->NewObject(cls, ctor, dict_name, frequencies);
        env->DeleteLocalRef(dict_name);
        env->DeleteLocalRef(frequencies);
        return out;
    }

    jobjectArray
    new_frequency_entry_array(JNIEnv *env, const std::vector<FrequencyEntry> &entries) {
        jclass cls = env->FindClass("chimahon/FrequencyEntry");
        jobjectArray array = env->NewObjectArray(static_cast<jsize>(entries.size()), cls, nullptr);
        for (size_t i = 0; i < entries.size(); ++i) {
            jobject item = new_frequency_entry(env, entries[i]);
            env->SetObjectArrayElement(array, static_cast<jsize>(i), item);
            env->DeleteLocalRef(item);
        }
        return array;
    }

    jobject new_pitch_entry(JNIEnv *env, const PitchEntry &entry) {
        jclass cls = env->FindClass("chimahon/PitchEntry");
        jmethodID ctor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;[I)V");
        jstring dict_name = new_string(env, entry.dict_name);
        jintArray positions = env->NewIntArray(static_cast<jsize>(entry.pitch_positions.size()));
        if (!entry.pitch_positions.empty()) {
            env->SetIntArrayRegion(positions, 0, static_cast<jsize>(entry.pitch_positions.size()),
                                   reinterpret_cast<const jint *>(entry.pitch_positions.data()));
        }
        jobject out = env->NewObject(cls, ctor, dict_name, positions);
        env->DeleteLocalRef(dict_name);
        env->DeleteLocalRef(positions);
        return out;
    }

    jobjectArray new_pitch_entry_array(JNIEnv *env, const std::vector<PitchEntry> &entries) {
        jclass cls = env->FindClass("chimahon/PitchEntry");
        jobjectArray array = env->NewObjectArray(static_cast<jsize>(entries.size()), cls, nullptr);
        for (size_t i = 0; i < entries.size(); ++i) {
            jobject item = new_pitch_entry(env, entries[i]);
            env->SetObjectArrayElement(array, static_cast<jsize>(i), item);
            env->DeleteLocalRef(item);
        }
        return array;
    }

    jobject new_term_result(JNIEnv *env, const TermResult &term) {
        jclass cls = env->FindClass("chimahon/TermResult");
        jmethodID ctor = env->GetMethodID(cls, "<init>",
                                          "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Lchimahon/GlossaryEntry;[Lchimahon/FrequencyEntry;[Lchimahon/PitchEntry;)V");
        jstring expression = new_string(env, term.expression);
        jstring reading = new_string(env, term.reading);
        jstring rules = new_string(env, term.rules);
        jobjectArray glossaries = new_glossary_entry_array(env, term.glossaries);
        jobjectArray frequencies = new_frequency_entry_array(env, term.frequencies);
        jobjectArray pitches = new_pitch_entry_array(env, term.pitches);
        jobject out = env->NewObject(cls, ctor, expression, reading, rules, glossaries, frequencies,
                                     pitches);
        env->DeleteLocalRef(expression);
        env->DeleteLocalRef(reading);
        env->DeleteLocalRef(rules);
        env->DeleteLocalRef(glossaries);
        env->DeleteLocalRef(frequencies);
        env->DeleteLocalRef(pitches);
        return out;
    }

    jobject new_lookup_result(JNIEnv *env, const LookupResult &result) {
        jclass cls = env->FindClass("chimahon/LookupResult");
        jmethodID ctor = env->GetMethodID(cls, "<init>",
                                          "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Lchimahon/TermResult;I)V");
        jstring matched = new_string(env, result.matched);
        jstring deinflected = new_string(env, result.deinflected);
        jobjectArray process = new_process_array(env, result.process);
        jobject term = new_term_result(env, result.term);
        jobject out = env->NewObject(cls, ctor, matched, deinflected, process, term,
                                     static_cast<jint>(result.preprocessor_steps));
        env->DeleteLocalRef(matched);
        env->DeleteLocalRef(deinflected);
        env->DeleteLocalRef(process);
        env->DeleteLocalRef(term);
        return out;
    }

    jobjectArray new_lookup_result_array(JNIEnv *env, const std::vector<LookupResult> &results) {
        jclass cls = env->FindClass("chimahon/LookupResult");
        jobjectArray array = env->NewObjectArray(static_cast<jsize>(results.size()), cls, nullptr);
        for (size_t i = 0; i < results.size(); ++i) {
            jobject item = new_lookup_result(env, results[i]);
            env->SetObjectArrayElement(array, static_cast<jsize>(i), item);
            env->DeleteLocalRef(item);
        }
        return array;
    }

    jobject new_dictionary_style(JNIEnv *env, const DictionaryStyle &style) {
        jclass cls = env->FindClass("chimahon/DictionaryStyle");
        jmethodID ctor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
        jstring dict_name = new_string(env, style.dict_name);
        jstring styles = new_string(env, style.styles);
        jobject out = env->NewObject(cls, ctor, dict_name, styles);
        env->DeleteLocalRef(dict_name);
        env->DeleteLocalRef(styles);
        return out;
    }

    jobjectArray
    new_dictionary_style_array(JNIEnv *env, const std::vector<DictionaryStyle> &styles) {
        jclass cls = env->FindClass("chimahon/DictionaryStyle");
        jobjectArray array = env->NewObjectArray(static_cast<jsize>(styles.size()), cls, nullptr);
        for (size_t i = 0; i < styles.size(); ++i) {
            jobject entry = new_dictionary_style(env, styles[i]);
            env->SetObjectArrayElement(array, static_cast<jsize>(i), entry);
            env->DeleteLocalRef(entry);
        }
        return array;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_chimahon_HoshiDicts_createLookupObject(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new LookupObject());
}

extern "C" JNIEXPORT void JNICALL
Java_chimahon_HoshiDicts_destroyLookupObject(JNIEnv *, jobject, jlong session) {
    delete as_object(session);
}

extern "C" JNIEXPORT void JNICALL
Java_chimahon_HoshiDicts_rebuildQuery(JNIEnv *env, jobject, jlong session,
                                              jobjectArray term_paths, jobjectArray freq_paths,
                                              jobjectArray pitch_paths) {
    LookupObject *obj = as_object(session);
    obj->query = DictionaryQuery{};
    for_each_string(env, term_paths,
                    [&](const std::string &path) { obj->query.add_term_dict(path); });
    for_each_string(env, freq_paths,
                    [&](const std::string &path) { obj->query.add_freq_dict(path); });
    for_each_string(env, pitch_paths,
                    [&](const std::string &path) { obj->query.add_pitch_dict(path); });
    obj->lookup = std::make_unique<Lookup>(obj->query, obj->deconjugator);
}

extern "C" JNIEXPORT jobject JNICALL
Java_chimahon_HoshiDicts_importDictionary(JNIEnv *env, jobject, jstring zip_path,
                                                  jstring output_dir) {
    auto zip_path_str = jstring_to_std_string(env, zip_path);
    auto output_dir_str = jstring_to_std_string(env, output_dir);
    const auto result = dictionary_importer::import(zip_path_str, output_dir_str, true);
    return new_import_result(env, result.success, static_cast<jlong>(result.term_count),
                             static_cast<jlong>(result.meta_count),
                             static_cast<jlong>(result.media_count));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_chimahon_HoshiDicts_lookup(JNIEnv *env, jobject, jlong session, jstring text,
                                        jint max_results) {
    LookupObject *obj = as_object(session);
    auto text_str = jstring_to_std_string(env, text);
    auto result = obj->lookup->lookup(text_str, static_cast<int>(max_results));
    return new_lookup_result_array(env, result);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_chimahon_HoshiDicts_getStyles(JNIEnv *env, jobject, jlong session) {
    LookupObject *obj = as_object(session);
    auto styles = obj->query.get_styles();
    return new_dictionary_style_array(env, styles);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_chimahon_HoshiDicts_getMediaFile(JNIEnv *env, jobject, jlong session,
                                              jstring dict_name, jstring media_path) {
    LookupObject *obj = as_object(session);
    auto dict_name_str = jstring_to_std_string(env, dict_name);
    auto media_path_str = jstring_to_std_string(env, media_path);
    auto data = obj->query.get_media_file(dict_name_str, media_path_str);
    if (data.empty()) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(data.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(data.size()),
                            reinterpret_cast<const jbyte *>(data.data()));
    return result;
}