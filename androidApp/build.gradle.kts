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
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
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
