plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    // The installed package is Flambo's, while the Kotlin/R namespace stays
    // RecordMaster's to avoid a risky full source-package rename.
    namespace = "com.pranshulgg.recordmaster"
    compileSdk = 36

    // Native STT: whisper.cpp is built from the pinned git submodule in
    // app/src/main/cpp/whisper.cpp. Keep this in sync with CI.
    ndkVersion = "27.0.12077973"

    signingConfigs {
        create("release") {
            val ciKeystore = file("release.keystore")
            val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
            when {
                ciKeystore.exists() -> {
                    storeFile = ciKeystore
                    storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "flambo123"
                    keyAlias = System.getenv("KEY_ALIAS") ?: "flambo"
                    keyPassword = System.getenv("KEY_PASSWORD") ?: "flambo123"
                }
                debugKeystore.exists() -> {
                    storeFile = debugKeystore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
                else -> {
                    storeFile = debugKeystore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.flambo.recorder"
        minSdk = 24
        targetSdk = 36
        versionCode = System.getenv("FLAMBO_VERSION_CODE")?.toIntOrNull() ?: 2
        versionName = System.getenv("FLAMBO_VERSION_NAME") ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64 is the primary target; armeabi-v7a covers older 32-bit
            // devices and x86_64 keeps emulator testing possible.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DBUILD_SHARED_LIBS=OFF",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF",
                    "-DWHISPER_BUILD_SERVER=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_NATIVE=OFF"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures {
        compose = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.animation)
    implementation(libs.colorpicker)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.materialKolor)
    implementation(libs.markdown.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.guava.listenablefuture)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.documentfile)
}