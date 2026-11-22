import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    sourceSets {
        getByName("desktopMain").dependencies {
            implementation(projects.shared)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.mochiledger.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "MochiLedger"
            packageVersion = "1.0.8"
            description = "墨记 MochiLedger — 跨端开源记账软件"
            vendor = "ackd7"
            modules("java.sql", "java.naming", "java.logging", "jdk.crypto.ec")

            windows {
                menuGroup = "MochiLedger"
                upgradeUuid = "b7a5e8f2-4c91-4d32-9e11-7d5c2b9a4f01"
                shortcut = true
                iconFile.set(project.file("packaging/windows/icon.ico"))
            }
        }
    }
}
