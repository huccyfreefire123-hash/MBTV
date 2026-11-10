plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ondatv.player"
    // The SDK installs API 37 as "android-37.0"; a plain 37 looks for "android-37".
    compileSdk { version = release(37) { minorApiLevel = 0 } }

    defaultConfig {
        applicationId = "com.ondatv.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.0"
    }

    buildTypes {
        release {
            // Optimized build (much smoother than debug). Signed with the same key as the debug
            // APK so it installs over it; use a release key before publishing.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required because the YTXTube module uses java.time/nio on old Android.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME, shown in the settings screen.
        buildConfig = true
    }
    packaging {
        jniLibs {
            // Extracted .so files avoid "problem parsing the package" on old Samsung firmware.
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/com/android/build/**",
                "META-INF/version-control-info.textproto",
                "META-INF/*.version",
                "org/mozilla/javascript/resources/Messages_*.properties",
                "DebugProbesKt.bin",
                "META-INF/**/LICENSE.txt",
                "META-INF/**/LICENSE",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":ytxtube"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    val media3 = "1.10.1"
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    // @Preview of a screen in Android Studio (the renderer itself only goes in debug builds).
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Liquid-glass tab bar, the same one the YTXTube module uses.
    implementation("io.github.kyant0:backdrop:2.0.1")
}
