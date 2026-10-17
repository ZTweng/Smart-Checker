plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":composeApp"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

compose.desktop {
    application {
        mainClass = "org.samc.desktop.MainKt"
        nativeDistributions {
            packageName = "Smart Checker"
            packageVersion = "6.26.2"
            description = "SAMC活动跟进表智能检查"
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Pkg
            )
            windows {
                iconFile.set(rootProject.file("packaging/icons/smart-checker.ico"))
                menuGroup = "SAMC"
                perUserInstall = true
                shortcut = true
            }
            macOS {
                iconFile.set(rootProject.file("packaging/icons/smart-checker.icns"))
                bundleID = "org.samc.checkform"
                dockName = "Smart Checker"
            }
        }
    }
}
