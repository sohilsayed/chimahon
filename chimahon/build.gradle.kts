plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "mihon.chimahon"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(libs.okhttp.core)
    implementation(libs.image.decoder)
    implementation(androidx.corektx)
    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.coroutines.core)
    implementation(kotlinx.serialization.protobuf)
    implementation(kotlinx.serialization.json)
    
    // Compose
    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.animation)
    implementation(compose.ui.util)

    // ChimaReader
    implementation(platform(libs.coil.bom))
    implementation(libs.bundles.coil)
    implementation(libs.preferencektx)
    implementation(libs.compose.webview)
    implementation(libs.jsoup)
    implementation(libs.datastore.preferences)
    implementation(libs.bundles.media3)

    // Dependency injection
    implementation(libs.injekt)
    
    // Mihon core
    implementation(projects.core.common)
    implementation(libs.unifile)
}
