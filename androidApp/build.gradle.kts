plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) }
}

android {
    namespace = "org.samc.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.samc.checkform"
        minSdk = 26
        targetSdk = 35
        versionCode = 62602
        versionName = "6.26.2"
    }

    signingConfigs {
        create("release") {
            val path = System.getenv("SMART_CHECKER_KEYSTORE")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = System.getenv("SMART_CHECKER_STORE_PASSWORD")
                keyAlias = "smart-checker"
                keyPassword = System.getenv("SMART_CHECKER_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.poi:poi-ooxml:5.3.0")
}
