import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val visionApiKey: String = localProperties.getProperty("vision.api.key") ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace   = "com.chuckfarah.streaminghistory"
    compileSdk  = 36

    defaultConfig {
        applicationId          = "com.chuckfarah.streaminghistory"
        minSdk                 = 26
        targetSdk              = 36
        versionCode            = 1
        versionName            = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_VISION_API_KEY", """"$visionApiKey"""")
    }

    // Phase 1: sign the release build with the standard Android debug keystore so the APK
    // can be sideloaded for smoke testing without a production keystore.
    // Replace with a real production signingConfig before any public distribution.
    signingConfigs {
        create("phase1") {
            val home = System.getProperty("user.home")
            storeFile     = file("$home/.android/debug.keystore")
            storePassword = "android"
            keyAlias      = "androiddebugkey"
            keyPassword   = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig   = signingConfigs.getByName("phase1")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Lifecycle + Activity
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.activity.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.coroutines.android)

    // Fuzzy matching
    implementation(libs.fuzzywuzzy)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // ML Kit OCR (bundled on-device Text Recognition V2)
    implementation(libs.mlkit.text.recognition)

    // ── Unit tests ──────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.junit.ext)

    // ── Instrumented tests ──────────────────────────────────────────────────
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
}
