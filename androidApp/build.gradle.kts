plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.mochiledger.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mochiledger.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 19
        versionName = "1.6.0"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("unified") {
            storeFile = file(System.getenv("MOCHI_KEYSTORE_PATH") ?: "mochiledger.jks")
            storePassword = System.getenv("MOCHI_KEYSTORE_PASSWORD") ?: "mochiledger2026"
            keyAlias = System.getenv("MOCHI_KEY_ALIAS") ?: "mochiledger"
            keyPassword = System.getenv("MOCHI_KEY_PASSWORD") ?: "mochiledger2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("unified")
        }
        release {
            signingConfig = signingConfigs.getByName("unified")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // Mochi Compose for Android liquid-glass / iOS-style motion integration
    implementation(libs.mochi.compose)
    // LSPosed / Xposed compileOnly API (provided at runtime by LSPosed framework)
    compileOnly(libs.xposed.api)
}
