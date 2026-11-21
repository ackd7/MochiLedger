plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val hasAndroidSdk = !System.getenv("ANDROID_HOME").isNullOrBlank() ||
    !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() ||
    rootProject.file("local.properties").exists() ||
    providers.gradleProperty("includeAndroid").orNull == "true"

if (hasAndroidSdk) {
    apply(plugin = libs.plugins.androidKotlinMultiplatformLibrary.get().pluginId)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    if (hasAndroidSdk) {
        extensions.findByName("android")?.let { androidExt ->
            // Configured via AGP 9 KMP library extension when Android SDK is available
            val clazz = androidExt.javaClass
            clazz.getMethod("setNamespace", String::class.java).invoke(androidExt, "com.mochiledger.shared")
            clazz.getMethod("setCompileSdk", Int::class.javaPrimitiveType).invoke(androidExt, 36)
            clazz.getMethod("setMinSdk", Int::class.javaPrimitiveType).invoke(androidExt, 26)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.miuix.ui)
            api(libs.miuix.preference)
            api(libs.miuix.icons)
            api(libs.miuix.squircle)
            api(libs.miuix.blur)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.sqlite.jdbc)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit)
            }
        }

        if (hasAndroidSdk) {
            findByName("androidMain")?.dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.core.ktx)
            }
        }
    }
}
