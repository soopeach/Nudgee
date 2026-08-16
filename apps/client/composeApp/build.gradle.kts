import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
}

fun localProperty(name: String): String = localProperties.getProperty(name, "").replace("\\", "\\\\").replace("\"", "\\\"")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.googleServices)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain = getByName("commonMain")
        val androidMain = getByName("androidMain")
        val desktopMain = getByName("desktopMain")

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(platform("io.github.jan-tennert.supabase:bom:${libs.versions.supabase.get()}"))
            implementation("io.github.jan-tennert.supabase:auth-kt")
            implementation("io.github.jan-tennert.supabase:functions-kt")
            implementation("io.github.jan-tennert.supabase:postgrest-kt")
            implementation("io.github.jan-tennert.supabase:realtime-kt")
            implementation("io.ktor:ktor-client-core:${libs.versions.ktor.get()}")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${libs.versions.serialization.get()}")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("io.coil-kt.coil3:coil-compose:${libs.versions.coil.get()}")
            implementation("io.coil-kt.coil3:coil-network-ktor3:${libs.versions.coil.get()}")
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation("io.ktor:ktor-client-okhttp:${libs.versions.ktor.get()}")
            implementation(platform("com.google.firebase:firebase-bom:${libs.versions.firebase.bom.get()}"))
            implementation("com.google.firebase:firebase-messaging")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:${libs.versions.ktor.get()}")
        }
    }
}

android {
    namespace = "com.soopeach.nudgee.client"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.soopeach.nudgee.client"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${localProperty("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${localProperty("supabase.publishableKey")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.soopeach.nudgee.client.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Nudgee"
            packageVersion = "1.0.0"
        }
    }
}
