plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hh.music.player"
    // 36: miuix-android 0.8.8 (latest usable with the runner SDK — Google only
    // publishes up to platforms;android-36; the 0.9.x split artifacts require
    // compileSdk 37 which does not exist). targetSdk stays 35.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hh.music.player"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Lint is not part of CI; release build only runs R8 verification.
    lint {
        checkReleaseBuilds = false
    }
}

// Kotlin 2.3 removed the String-based `kotlinOptions.jvmTarget`; use the typed DSL.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.ui.tooling)

    // JVM unit tests: pure logic (PlaybackEngine / LyricParser / QueueCodec / Repository)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")

    // QR rendering for NetEase login (core only — no camera/scanner)
    implementation("com.google.zxing:core:3.5.3")

    // Miuix UI (Xiaomi HyperOS design language). 0.8.8 is the last single
    // artifact with the top.yukonga.miuix.kmp.theme API; 0.9.x split artifacts
    // require compileSdk 37 (not published). Only the theme is used here.
    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.8")
}
