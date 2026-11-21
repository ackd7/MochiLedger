rootProject.name = "MochiLedger"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
    }
}

val hasAndroidSdk = !System.getenv("ANDROID_HOME").isNullOrBlank() ||
    !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() ||
    file("local.properties").exists() ||
    providers.gradleProperty("includeAndroid").orNull == "true"

include(":shared")
include(":desktopApp")
include(":syncServer")

if (hasAndroidSdk) {
    include(":androidApp")
} else {
    println("[MochiLedger] Android SDK not detected locally; skipping :androidApp. Set ANDROID_HOME or -PincludeAndroid=true to include it.")
}
