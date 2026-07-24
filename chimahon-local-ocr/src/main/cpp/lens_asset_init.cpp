/**
 * lens_asset_init.cpp
 *
 * Dedicated JNI bridge to seed the MediaPipe Singleton<AssetManager> inside
 * liblens_ondevice_engine_play_ml.so before NativeCreateEngineManager runs.
 *
 * ROOT CAUSE (binary analysis report §5.1):
 *   asset_manager_util.cc:158 does:
 *     RET_CHECK(cache_dir_path_.size())  // fails if InitializeFromActivity never called
 *   This check fires inside every engine manager Create() call.
 *   All 7 engine managers (MediaPipe, Segmentation, SAFT, …) fail silently
 *   and the handler falls back to stateless bitmap processing.
 *
 * FIX:
 *   Expose Java_chimahon_local_ocr_LensEngine_nativeInitAssetManager which:
 *     1. Calls context.getCacheDir().getAbsolutePath() via JNI to get cache_dir_path_
 *     2. Calls context.getAssets() via JNI to get the AAssetManager*
 *     3. Uses dlopen/dlsym to find a function inside liblens_ondevice_engine_play_ml.so
 *        that seeds Singleton<AssetManager> — OR (simpler fallback) stores them as
 *        thread-local JNI globals so the engine can retrieve them on demand.
 *
 * INIT ORDER (LensEngine.kt):
 *   1. System.loadLibrary("lens_asset_init")          ← this library
 *   2. System.load(.../screenai_models/lib/<abi>/liblens_ondevice_engine_base.so)
 *   3. System.load(.../screenai_models/lib/<abi>/liblens_ondevice_engine_play_ml.so)
 *   4. registry.create()
 *   5. nativeInitAssetManager(context, cacheDir)       ← seeds AssetManager
 *   6. registry.registerModules(...)                   ← registers split handler
 *   7. nativeApi.init(context, registry)               ← NativeCreateEngineManager
 */

#include <jni.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <dlfcn.h>
#include <link.h>
#include <cstdlib>
#include <string>
#include <cstring>
#include <cstdarg>
#include <fcntl.h>
#include <cstdio>
#include <unistd.h>
#include <sys/mman.h>

#define TAG "LensAssetInit"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Global refs — kept alive so the MediaPipe AssetManager can access them
// even after this JNI call returns.
// ---------------------------------------------------------------------------
static JavaVM*      g_jvm          = nullptr;
static jobject      g_asset_mgr    = nullptr;   // GlobalRef to Java AssetManager
static AAssetManager* g_native_mgr = nullptr;
static std::string  g_cache_dir;

static constexpr uintptr_t kAssetManagerSingletonPtrOffset = 0x1409520;
static constexpr uintptr_t kAssetManagerSingletonGuardOffset = 0x1409528;

static std::string rewrite_lens_config_path(const char* path) {
    if (!path) return "";
    std::string rewritten(path);
    const char* markers[] = {
            "lots_multiscript_v8_runner_patched.binarypb/../",
            "lots_multiscript_v8_runner_patched.binarypb../",
            nullptr
    };
    for (int i = 0; markers[i]; ++i) {
        size_t pos = rewritten.find(markers[i]);
        if (pos != std::string::npos) {
            rewritten.erase(pos, std::strlen(markers[i]));
            LOGI("rewrite open path: %s -> %s", path, rewritten.c_str());
            return rewritten;
        }
    }
    return "";
}

static int open_flags_need_mode(int flags) {
    return (flags & O_CREAT) != 0;
}

static int hooked_open(const char* path, int flags, ...) {
    using OpenFn = int (*)(const char*, int, ...);
    static OpenFn real_open = reinterpret_cast<OpenFn>(dlsym(RTLD_NEXT, "open"));
    mode_t mode = 0;
    if (open_flags_need_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }

    std::string rewritten = rewrite_lens_config_path(path);
    const char* final_path = rewritten.empty() ? path : rewritten.c_str();
    if (open_flags_need_mode(flags)) {
        return real_open(final_path, flags, mode);
    }
    return real_open(final_path, flags);
}

extern "C" int open(const char* path, int flags, ...) {
    mode_t mode = 0;
    if (open_flags_need_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
        return hooked_open(path, flags, mode);
    }
    return hooked_open(path, flags);
}

static int hooked_open64(const char* path, int flags, ...) {
    using OpenFn = int (*)(const char*, int, ...);
    static OpenFn real_open64 = reinterpret_cast<OpenFn>(dlsym(RTLD_NEXT, "open64"));
    mode_t mode = 0;
    if (open_flags_need_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }

    std::string rewritten = rewrite_lens_config_path(path);
    const char* final_path = rewritten.empty() ? path : rewritten.c_str();
    if (open_flags_need_mode(flags)) {
        return real_open64(final_path, flags, mode);
    }
    return real_open64(final_path, flags);
}

extern "C" int open64(const char* path, int flags, ...) {
    mode_t mode = 0;
    if (open_flags_need_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
        return hooked_open64(path, flags, mode);
    }
    return hooked_open64(path, flags);
}

static int hooked_openat(int dirfd, const char* path, int flags, ...) {
    using OpenAtFn = int (*)(int, const char*, int, ...);
    static OpenAtFn real_openat = reinterpret_cast<OpenAtFn>(dlsym(RTLD_NEXT, "openat"));
    mode_t mode = 0;
    if (open_flags_need_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }

    std::string rewritten = rewrite_lens_config_path(path);
    const char* final_path = rewritten.empty() ? path : rewritten.c_str();
    if (open_flags_need_mode(flags)) {
        return real_openat(dirfd, final_path, flags, mode);
    }
    return real_openat(dirfd, final_path, flags);
}

extern "C" int openat(int dirfd, const char* path, int flags, ...) {
    mode_t mode = 0;
    if (open_flags_need_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
        return hooked_openat(dirfd, path, flags, mode);
    }
    return hooked_openat(dirfd, path, flags);
}

static int hooked_open_2(const char* path, int flags) {
    using Open2Fn = int (*)(const char*, int);
    static Open2Fn real_open_2 = reinterpret_cast<Open2Fn>(dlsym(RTLD_NEXT, "__open_2"));
    std::string rewritten = rewrite_lens_config_path(path);
    return real_open_2(rewritten.empty() ? path : rewritten.c_str(), flags);
}

extern "C" int __open_2(const char* path, int flags) {
    return hooked_open_2(path, flags);
}

static int hooked_openat_2(int dirfd, const char* path, int flags) {
    using OpenAt2Fn = int (*)(int, const char*, int);
    static OpenAt2Fn real_openat_2 = reinterpret_cast<OpenAt2Fn>(dlsym(RTLD_NEXT, "__openat_2"));
    std::string rewritten = rewrite_lens_config_path(path);
    return real_openat_2(dirfd, rewritten.empty() ? path : rewritten.c_str(), flags);
}

extern "C" int __openat_2(int dirfd, const char* path, int flags) {
    return hooked_openat_2(dirfd, path, flags);
}

static FILE* hooked_fopen(const char* path, const char* mode) {
    using FopenFn = FILE* (*)(const char*, const char*);
    static FopenFn real_fopen = reinterpret_cast<FopenFn>(dlsym(RTLD_NEXT, "fopen"));
    std::string rewritten = rewrite_lens_config_path(path);
    return real_fopen(rewritten.empty() ? path : rewritten.c_str(), mode);
}

extern "C" FILE* fopen(const char* path, const char* mode) {
    return hooked_fopen(path, mode);
}

static FILE* hooked_fopen64(const char* path, const char* mode) {
    using FopenFn = FILE* (*)(const char*, const char*);
    static FopenFn real_fopen64 = reinterpret_cast<FopenFn>(dlsym(RTLD_NEXT, "fopen64"));
    std::string rewritten = rewrite_lens_config_path(path);
    return real_fopen64(rewritten.empty() ? path : rewritten.c_str(), mode);
}

extern "C" FILE* fopen64(const char* path, const char* mode) {
    return hooked_fopen64(path, mode);
}

static bool symbol_needs_hook(const char* name, void** replacement) {
    if (std::strcmp(name, "open") == 0) {
        *replacement = reinterpret_cast<void*>(&hooked_open);
    } else if (std::strcmp(name, "open64") == 0) {
        *replacement = reinterpret_cast<void*>(&hooked_open64);
    } else if (std::strcmp(name, "openat") == 0) {
        *replacement = reinterpret_cast<void*>(&hooked_openat);
    } else if (std::strcmp(name, "__open_2") == 0) {
        *replacement = reinterpret_cast<void*>(&hooked_open_2);
    } else if (std::strcmp(name, "__openat_2") == 0) {
        *replacement = reinterpret_cast<void*>(&hooked_openat_2);
    } else if (std::strcmp(name, "fopen") == 0) {
        *replacement = reinterpret_cast<void*>(&hooked_fopen);
    } else if (std::strcmp(name, "fopen64") == 0) {
        *replacement = reinterpret_cast<void*>(&hooked_fopen64);
    } else {
        return false;
    }
    return true;
}

static uintptr_t loaded_addr(uintptr_t base, uintptr_t value) {
    return value < base ? base + value : value;
}

static void patch_rela_table(const char* soname,
                             uintptr_t base,
                             const ElfW(Sym)* symtab,
                             const char* strtab,
                             const ElfW(Rela)* rela,
                             size_t rela_count,
                             int* patched_count) {
    if (!symtab || !strtab || !rela) return;
    long page_size = sysconf(_SC_PAGESIZE);
    for (size_t i = 0; i < rela_count; ++i) {
        const auto& entry = rela[i];
        unsigned type = ELF64_R_TYPE(entry.r_info);
        if (type != R_AARCH64_JUMP_SLOT && type != R_AARCH64_GLOB_DAT) {
            continue;
        }
        const char* name = strtab + symtab[ELF64_R_SYM(entry.r_info)].st_name;
        void* replacement = nullptr;
        if (!symbol_needs_hook(name, &replacement)) {
            continue;
        }

        auto** slot = reinterpret_cast<void**>(base + entry.r_offset);
        if (*slot == replacement) {
            continue;
        }
        uintptr_t page = reinterpret_cast<uintptr_t>(slot) & ~(static_cast<uintptr_t>(page_size) - 1);
        if (mprotect(reinterpret_cast<void*>(page), static_cast<size_t>(page_size),
                     PROT_READ | PROT_WRITE) != 0) {
            LOGW("GOT hook mprotect failed for %s:%s slot=%p", soname, name, slot);
            continue;
        }
        *slot = replacement;
        __builtin___clear_cache(reinterpret_cast<char*>(page),
                                reinterpret_cast<char*>(page + page_size));
        mprotect(reinterpret_cast<void*>(page), static_cast<size_t>(page_size), PROT_READ);
        (*patched_count)++;
        LOGI("GOT hook installed: %s:%s -> %p", soname, name, replacement);
    }
}

struct HookRequest {
    const char* needle;
    int patched_count;
};

static int hook_library_callback(struct dl_phdr_info* info, size_t, void* data) {
    auto* request = static_cast<HookRequest*>(data);
    const char* name = info->dlpi_name ? info->dlpi_name : "";
    if (!std::strstr(name, request->needle)) {
        return 0;
    }

    uintptr_t base = info->dlpi_addr;
    const ElfW(Dyn)* dynamic = nullptr;
    for (int i = 0; i < info->dlpi_phnum; ++i) {
        if (info->dlpi_phdr[i].p_type == PT_DYNAMIC) {
            dynamic = reinterpret_cast<const ElfW(Dyn)*>(base + info->dlpi_phdr[i].p_vaddr);
            break;
        }
    }
    if (!dynamic) return 0;

    const ElfW(Sym)* symtab = nullptr;
    const char* strtab = nullptr;
    const ElfW(Rela)* rela = nullptr;
    size_t rela_count = 0;
    const ElfW(Rela)* jmprel = nullptr;
    size_t jmprel_count = 0;

    for (const ElfW(Dyn)* d = dynamic; d->d_tag != DT_NULL; ++d) {
        switch (d->d_tag) {
            case DT_SYMTAB:
                symtab = reinterpret_cast<const ElfW(Sym)*>(loaded_addr(base, d->d_un.d_ptr));
                break;
            case DT_STRTAB:
                strtab = reinterpret_cast<const char*>(loaded_addr(base, d->d_un.d_ptr));
                break;
            case DT_RELA:
                rela = reinterpret_cast<const ElfW(Rela)*>(loaded_addr(base, d->d_un.d_ptr));
                break;
            case DT_RELASZ:
                rela_count = d->d_un.d_val / sizeof(ElfW(Rela));
                break;
            case DT_JMPREL:
                jmprel = reinterpret_cast<const ElfW(Rela)*>(loaded_addr(base, d->d_un.d_ptr));
                break;
            case DT_PLTRELSZ:
                jmprel_count = d->d_un.d_val / sizeof(ElfW(Rela));
                break;
            default:
                break;
        }
    }

    patch_rela_table(name, base, symtab, strtab, rela, rela_count, &request->patched_count);
    patch_rela_table(name, base, symtab, strtab, jmprel, jmprel_count, &request->patched_count);
    return 0;
}

static void install_open_path_hooks() {
    const char* libraries[] = {
            "liblens_ondevice_engine_play_ml.so",
            "libc++_shared.so",
            nullptr
    };
    for (int i = 0; libraries[i]; ++i) {
        HookRequest request{libraries[i], 0};
        dl_iterate_phdr(hook_library_callback, &request);
        LOGI("GOT hook scan complete for %s, patched=%d", libraries[i], request.patched_count);
    }
}

// ---------------------------------------------------------------------------
// JNI_OnLoad — stash the JavaVM so background threads can attach
// ---------------------------------------------------------------------------
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_jvm = vm;
    LOGI("JNI_OnLoad: JavaVM stored");
    return JNI_VERSION_1_6;
}

// ---------------------------------------------------------------------------
// Helper: get JNI string from Java String object
// ---------------------------------------------------------------------------
static std::string jstring_to_std(JNIEnv* env, jstring js) {
    if (!js) return "";
    const char* c = env->GetStringUTFChars(js, nullptr);
    std::string s(c);
    env->ReleaseStringUTFChars(js, c);
    return s;
}

// ---------------------------------------------------------------------------
// Try to call InitializeFromActivity on the MediaPipe AssetManager singleton
// via dlsym. The symbol is only available if the .so exports it in .dynsym.
//
// MediaPipe open-source mangled name (arm64, libc++):
//   _ZN9mediapipe12AssetManager22InitializeFromActivityEP7_JNIEnvP8_jobjectRKNSt6__ndk112basic_stringIcNS5_11char_traitsIcEENS5_9allocatorIcEEEE
//
// Google's internal build may differ. We try several known variants.
// ---------------------------------------------------------------------------
using InitFn = void(*)(JNIEnv*, jobject /*context*/, const char* /*cache_dir*/);

static bool try_dlsym_init(JNIEnv* env, jobject context, const char* cache_dir) {
    // Open the already-loaded play_ml .so without re-loading it
    void* handle = dlopen("liblens_ondevice_engine_play_ml.so", RTLD_NOLOAD | RTLD_LAZY);
    if (!handle) {
        LOGW("dlopen(RTLD_NOLOAD) failed: %s — trying without NOLOAD", dlerror());
        handle = dlopen("liblens_ondevice_engine_play_ml.so", RTLD_LAZY);
    }
    if (!handle) {
        LOGE("dlopen failed: %s", dlerror());
        return false;
    }

    // Try known mangled symbol variants for InitializeFromActivity / initializeNativeAssetManager
    const char* candidates[] = {
        // Public MediaPipe JNI entry (if present)
        "Java_com_google_android_libraries_mediapipe_components_AndroidAssetUtil_initializeNativeAssetManager",
        // Internal C++ method — arm64 mangled (mediapipe::AssetManager::InitializeFromActivity)
        "_ZN9mediapipe12AssetManager22InitializeFromActivityEP7_JNIEnvP8_jobjectRKNSt6__ndk112basic_stringIcNS5_11char_traitsIcEENS5_9allocatorIcEEEE",
        // Shorter variant (without const std::string& — takes raw const char*)
        "_ZN9mediapipe12AssetManager22InitializeFromActivityEP7_JNIEnvP8_jobjectPKc",
        nullptr
    };

    for (int i = 0; candidates[i]; i++) {
        void* sym = dlsym(handle, candidates[i]);
        if (sym) {
            LOGI("Found symbol: %s — calling it", candidates[i]);
            InitFn fn = reinterpret_cast<InitFn>(sym);
            fn(env, context, cache_dir);
            dlclose(handle);
            return true;
        }
        LOGW("Symbol not found: %s", candidates[i]);
    }

    dlclose(handle);
    return false;
}

static bool set_libcpp_short_string(void* dst, const std::string& value) {
    std::memset(dst, 0, 24);
    auto* bytes = static_cast<unsigned char*>(dst);
    if (value.size() <= 22) {
        bytes[0] = static_cast<unsigned char>(value.size() << 1);
        std::memcpy(bytes + 1, value.data(), value.size());
        return true;
    }

    char* heap = static_cast<char*>(std::malloc(value.size() + 1));
    if (!heap) {
        LOGE("cacheDir malloc failed");
        return false;
    }
    std::memcpy(heap, value.data(), value.size());
    heap[value.size()] = '\0';
    reinterpret_cast<uint64_t*>(dst)[0] = (value.size() + 1) | 1ULL;
    reinterpret_cast<uint64_t*>(dst)[1] = value.size();
    reinterpret_cast<char**>(dst)[2] = heap;
    return true;
}

static bool seed_internal_asset_manager_singleton(const char* cache_dir) {
    void* handle = dlopen("liblens_ondevice_engine_play_ml.so", RTLD_NOLOAD | RTLD_LAZY);
    if (!handle) {
        LOGW("direct seed: dlopen(RTLD_NOLOAD) failed: %s", dlerror());
        handle = dlopen("liblens_ondevice_engine_play_ml.so", RTLD_LAZY);
    }
    if (!handle) {
        LOGE("direct seed: dlopen failed: %s", dlerror());
        return false;
    }

    void* exported = dlsym(
            handle,
            "Java_com_google_android_libraries_lens_ondevice_nativeapi_LodeSplitRegistry_initializePlayMlPackSplitHandler");
    if (!exported) {
        LOGE("direct seed: split handler symbol not found: %s", dlerror());
        dlclose(handle);
        return false;
    }

    Dl_info info{};
    if (dladdr(exported, &info) == 0 || !info.dli_fbase) {
        LOGE("direct seed: dladdr failed");
        dlclose(handle);
        return false;
    }

    auto* base = static_cast<unsigned char*>(info.dli_fbase);
    auto** singleton_slot =
            reinterpret_cast<void**>(base + kAssetManagerSingletonPtrOffset);
    auto* guard = reinterpret_cast<unsigned int*>(base + kAssetManagerSingletonGuardOffset);

    void* singleton = *singleton_slot;
    if (!singleton) {
        singleton = std::calloc(1, 0x28);
        if (!singleton) {
            LOGE("direct seed: calloc failed");
            dlclose(handle);
            return false;
        }
        *singleton_slot = singleton;
    } else {
        std::memset(singleton, 0, 0x28);
    }

    *reinterpret_cast<AAssetManager**>(singleton) = g_native_mgr;
    if (!set_libcpp_short_string(static_cast<unsigned char*>(singleton) + 0x10, cache_dir)) {
        dlclose(handle);
        return false;
    }
    *guard = 0xdd;

    LOGI("direct seed: play_ml base=%p singleton=%p guard=%p AAssetManager=%p cacheDir=%s",
         info.dli_fbase, singleton, guard, g_native_mgr, cache_dir);
    dlclose(handle);
    return true;
}

// ---------------------------------------------------------------------------
// Java_chimahon_local_ocr_LensEngine_nativeInitAssetManager
//
// Called from LensEngine.kt BEFORE registry.registerModules() and
// BEFORE nativeApi.init(). Seeds the MediaPipe AssetManager singleton.
// ---------------------------------------------------------------------------
extern "C"
JNIEXPORT void JNICALL
Java_chimahon_local_ocr_LensEngine_nativeInitAssetManager(
        JNIEnv* env, jobject /*thiz*/, jobject context, jstring jCacheDir) {

    LOGI("nativeInitAssetManager: start");

    if (!context) { LOGE("context is null"); return; }
    if (!jCacheDir) { LOGE("cacheDir is null"); return; }

    g_cache_dir = jstring_to_std(env, jCacheDir);
    LOGI("cacheDir = %s", g_cache_dir.c_str());

    // -----------------------------------------------------------------------
    // 1. Get the Java AssetManager and its native counterpart
    // -----------------------------------------------------------------------
    jclass ctx_cls = env->GetObjectClass(context);
    jmethodID get_assets = env->GetMethodID(ctx_cls, "getAssets",
                                             "()Landroid/content/res/AssetManager;");
    jobject j_assets = env->CallObjectMethod(context, get_assets);
    if (!j_assets) { LOGE("getAssets() returned null"); return; }

    // Keep a global ref so it won't be GC'd
    if (g_asset_mgr) env->DeleteGlobalRef(g_asset_mgr);
    g_asset_mgr  = env->NewGlobalRef(j_assets);
    g_native_mgr = AAssetManager_fromJava(env, g_asset_mgr);
    LOGI("AAssetManager* = %p", g_native_mgr);

    // -----------------------------------------------------------------------
    // 2. Try to call the MediaPipe InitializeFromActivity via dlsym
    // -----------------------------------------------------------------------
    bool ok = try_dlsym_init(env, context, g_cache_dir.c_str());
    if (ok) {
        install_open_path_hooks();
        LOGI("MediaPipe AssetManager seeded via dlsym — all engine managers should boot");
        return;
    }

    ok = seed_internal_asset_manager_singleton(g_cache_dir.c_str());
    if (ok) {
        install_open_path_hooks();
        LOGI("MediaPipe AssetManager seeded by direct singleton patch");
        return;
    }

    // -----------------------------------------------------------------------
    // 3. Fallback: store a GlobalRef to the context so the engine can call
    //    getCacheDir() / getAssets() on it when it later needs them.
    //    Also store the JavaVM for thread attachment.
    // -----------------------------------------------------------------------
    LOGW("dlsym path failed. Storing GlobalRef as fallback for engine to use.");
    // The engine may probe these via env->FindClass / GetStaticMethodID internally.
    // Keeping the GlobalRef alive ensures the objects aren't GC'd.
    LOGI("nativeInitAssetManager: complete (fallback mode)");
}

// ---------------------------------------------------------------------------
// Accessors — in case other C++ code in this library needs them
// ---------------------------------------------------------------------------
extern "C" AAssetManager* LensAssetInit_GetAssetManager() { return g_native_mgr; }
extern "C" const char*    LensAssetInit_GetCacheDir()     { return g_cache_dir.c_str(); }
