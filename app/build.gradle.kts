plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.flambo.recorder"
    compileSdk = 37

    signingConfigs {
        create("release") {
            val ciKeystore = file("release.keystore")
            val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
            when {
                ciKeystore.exists() -> {
                    storeFile = ciKeystore
                    storePassword = System.getenv("KEYSTORE_PASSWORD") ?: (project.findProperty("FLAMBO_STORE_PASSWORD") as String? ?: "flambo123")
                    keyAlias = System.getenv("KEY_ALIAS") ?: (project.findProperty("FLAMBO_KEY_ALIAS") as String? ?: "flambo")
                    keyPassword = System.getenv("KEY_PASSWORD") ?: (project.findProperty("FLAMBO_KEY_PASSWORD") as String? ?: "flambo123")
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

    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.flambo.recorder"
        minSdk = 26
        targetSdk = 36
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        val baseCode = 1
        versionCode = System.getenv("FLAMBO_VERSION_CODE")?.toIntOrNull()
            ?: runNumber?.let { baseCode + it } ?: baseCode
        versionName = System.getenv("FLAMBO_VERSION_NAME") ?: "1.3.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_shared") } }
    }

    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.vosk.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
