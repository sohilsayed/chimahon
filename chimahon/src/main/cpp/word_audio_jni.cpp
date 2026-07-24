// word_audio_jni.cpp — mmap + sqlite3_deserialize bridge for SAF-backed
// read-only SQLite access. Bypasses FUSE Scoped Storage path restrictions.

#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>
#include <sqlite3.h>
#include <pthread.h>
#include <string>

static JavaVM* g_jvm = nullptr;

struct SafDb {
    sqlite3*  db;
    void*     map;
    size_t    size;
    int       fd;
};

static SafDb* as_handle(jlong h) { return reinterpret_cast<SafDb*>(static_cast<uintptr_t>(h)); }

// ─── JNI_OnLoad: cache JVM for later use ────────────────────────────────
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

// ─── nativeOpen: mmap fd, hand buffer to sqlite3_deserialize ────────────
extern "C" JNIEXPORT jlong JNICALL
Java_chimahon_audio_WordAudioDatabase_nativeOpen(JNIEnv* env, jobject /*thiz*/, jint fd, jlong size) {
    if (size <= 0) {
        close(fd);
        return 0;
    }

    void* map = mmap(nullptr, static_cast<size_t>(size), PROT_READ, MAP_SHARED, fd, 0);
    if (map == MAP_FAILED) {
        close(fd);
        return 0;
    }

    sqlite3* db = nullptr;
    int rc = sqlite3_open_v2(":memory:", &db,
        SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX, nullptr);
    if (rc != SQLITE_OK) {
        sqlite3_close(db);
        munmap(map, static_cast<size_t>(size));
        close(fd);
        return 0;
    }

    rc = sqlite3_deserialize(db, "main",
        static_cast<unsigned char*>(map),
        static_cast<sqlite3_int64>(size),
        static_cast<sqlite3_int64>(size),
        SQLITE_DESERIALIZE_READONLY);
    if (rc != SQLITE_OK) {
        sqlite3_close(db);
        munmap(map, static_cast<size_t>(size));
        close(fd);
        return 0;
    }

    // Read-only performance tuning
    sqlite3_exec(db, "PRAGMA query_only=1;", nullptr, nullptr, nullptr);
    sqlite3_exec(db, "PRAGMA journal_mode=OFF;", nullptr, nullptr, nullptr);
    sqlite3_exec(db, "PRAGMA synchronous=OFF;", nullptr, nullptr, nullptr);
    sqlite3_exec(db, "PRAGMA cache_size=-262144;", nullptr, nullptr, nullptr);
    sqlite3_exec(db, "PRAGMA temp_store=MEMORY;", nullptr, nullptr, nullptr);
    sqlite3_exec(db, "PRAGMA mmap_size=30000000000;", nullptr, nullptr, nullptr);
    sqlite3_exec(db, "PRAGMA page_size=4096;", nullptr, nullptr, nullptr);

    auto* s = new SafDb{ db, map, static_cast<size_t>(size), fd };
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(s));
}

// ─── nativeClose: SQLite → munmap → close fd (if present) ──────────────
extern "C" JNIEXPORT void JNICALL
Java_chimahon_audio_WordAudioDatabase_nativeClose(JNIEnv* /*env*/, jobject /*thiz*/, jlong h) {
    SafDb* s = as_handle(h);
    if (!s) return;
    sqlite3_close(s->db);
    if (s->fd >= 0) {
        // mmap path: unmap and close fd
        if (s->map) munmap(s->map, s->size);
        close(s->fd);
    }
    delete s;
}

// ─── nativeTestConnection: cheap probe ──────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_chimahon_audio_WordAudioDatabase_nativeTestConnection(JNIEnv* /*env*/, jobject /*thiz*/, jlong h) {
    SafDb* s = as_handle(h);
    if (!s) return JNI_FALSE;
    sqlite3_stmt* stmt = nullptr;
    jboolean ok = JNI_FALSE;
    if (sqlite3_prepare_v2(s->db,
            "SELECT count(*) FROM entries LIMIT 1",
            -1, &stmt, nullptr) == SQLITE_OK) {
        if (sqlite3_step(stmt) == SQLITE_ROW) ok = JNI_TRUE;
        sqlite3_finalize(stmt);
    }
    return ok;
}

// ─── nativeFindEntries: point lookup on (expression, reading) ──────────
extern "C" JNIEXPORT jobjectArray JNICALL
Java_chimahon_audio_WordAudioDatabase_nativeFindEntries(
        JNIEnv* env, jobject /*thiz*/, jlong h,
        jstring jTerm, jstring jReading, jstring jSourceOrder) {

    SafDb* s = as_handle(h);
    if (!s) return nullptr;

    const char* term  = jTerm        ? env->GetStringUTFChars(jTerm, nullptr)        : "";
    const char* read  = jReading     ? env->GetStringUTFChars(jReading, nullptr)     : "";
    const char* order = jSourceOrder ? env->GetStringUTFChars(jSourceOrder, nullptr) : "";

    // Build "CASE source WHEN 'a' THEN 0 WHEN 'b' THEN 1 ... ELSE 999 END"
    std::string orderClause = "CASE source ";
    {
        std::string list(order);
        int idx = 0;
        size_t start = 0;
        while (start <= list.size()) {
            size_t comma = list.find(',', start);
            std::string src = (comma == std::string::npos)
                ? list.substr(start) : list.substr(start, comma - start);
            if (!src.empty()) {
                char buf[128];
                snprintf(buf, sizeof(buf), "WHEN '%s' THEN %d ", src.c_str(), idx);
                orderClause += buf;
                ++idx;
            }
            if (comma == std::string::npos) break;
            start = comma + 1;
        }
        orderClause += "ELSE 999 END";
    }

    std::string sql;
    if (read[0] == '\0') {
        sql = "SELECT file, source, speaker, display, reading, expression "
              "FROM entries WHERE expression = ? "
              "ORDER BY " + orderClause + " LIMIT 1";
    } else {
        sql = "SELECT file, source, speaker, display, reading, expression "
              "FROM entries WHERE expression = ? OR reading = ? "
              "ORDER BY CASE WHEN reading = ? THEN 0 ELSE 1 END, "
              + orderClause + " LIMIT 1";
    }

    // Find LocalEntry class and constructor
    jclass entryCls = env->FindClass("chimahon/audio/WordAudioDatabase$LocalEntry");
    if (!entryCls) return nullptr;

    jmethodID ctor = env->GetMethodID(entryCls, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;"
        "Ljava/lang/String;Ljava/lang/String;"
        "Ljava/lang/String;Ljava/lang/String;)V");
    if (!ctor) return nullptr;

    sqlite3_stmt* stmt = nullptr;
    jobjectArray result = env->NewObjectArray(1, entryCls, nullptr);

    if (sqlite3_prepare_v2(s->db, sql.c_str(), -1, &stmt, nullptr) == SQLITE_OK) {
        sqlite3_bind_text(stmt, 1, term, -1, SQLITE_TRANSIENT);
        if (read[0] != '\0') {
            sqlite3_bind_text(stmt, 2, read, -1, SQLITE_TRANSIENT);
            sqlite3_bind_text(stmt, 3, read, -1, SQLITE_TRANSIENT);
        }

        if (sqlite3_step(stmt) == SQLITE_ROW) {
            auto js = [&](int col, bool allowNull) -> jstring {
                const unsigned char* v = sqlite3_column_text(stmt, col);
                if (!v && allowNull) return nullptr;
                return env->NewStringUTF(v ? reinterpret_cast<const char*>(v) : "");
            };

            jobject obj = env->NewObject(entryCls, ctor,
                js(0, false),  // file
                js(1, false),  // sourceId
                js(2, true),   // speaker (nullable)
                js(3, true),   // display (nullable)
                js(4, false),  // reading
                js(5, false)); // expression

            env->SetObjectArrayElement(result, 0, obj);
            env->DeleteLocalRef(obj);
        }
        sqlite3_finalize(stmt);
    }

    if (jTerm)        env->ReleaseStringUTFChars(jTerm, term);
    if (jReading)     env->ReleaseStringUTFChars(jReading, read);
    if (jSourceOrder) env->ReleaseStringUTFChars(jSourceOrder, order);

    env->DeleteLocalRef(entryCls);
    return result;
}

// ─── nativeGetAudioData: fetch BLOB from android(file, source) ──────────
extern "C" JNIEXPORT jbyteArray JNICALL
Java_chimahon_audio_WordAudioDatabase_nativeGetAudioData(
        JNIEnv* env, jobject /*thiz*/, jlong h,
        jstring jFile, jstring jSource) {

    SafDb* s = as_handle(h);
    if (!s) return nullptr;

    const char* file = jFile   ? env->GetStringUTFChars(jFile, nullptr)   : "";
    const char* src  = jSource ? env->GetStringUTFChars(jSource, nullptr) : "";

    jbyteArray out = nullptr;
    sqlite3_stmt* stmt = nullptr;

    if (sqlite3_prepare_v2(s->db,
            "SELECT data FROM android WHERE file = ? AND source = ?",
            -1, &stmt, nullptr) == SQLITE_OK) {
        sqlite3_bind_text(stmt, 1, file, -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(stmt, 2, src,  -1, SQLITE_TRANSIENT);

        if (sqlite3_step(stmt) == SQLITE_ROW) {
            const void* blob = sqlite3_column_blob(stmt, 0);
            int n = sqlite3_column_bytes(stmt, 0);
            if (blob && n > 0) {
                out = env->NewByteArray(n);
                env->SetByteArrayRegion(out, 0, n, static_cast<const jbyte*>(blob));
            }
        }
        sqlite3_finalize(stmt);
    }

    if (jFile)   env->ReleaseStringUTFChars(jFile, file);
    if (jSource) env->ReleaseStringUTFChars(jSource, src);
    return out;
}
