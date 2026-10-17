plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) }
    }
    jvm("desktop")

    sourceSets {
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        getByName("desktopMain").dependsOn(jvmSharedMain)
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "org.samc.core"
    compileSdk = 35
}
