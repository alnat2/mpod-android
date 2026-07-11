// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}

val localProperties = java.util.Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use(localProperties::load)
}

val androidSdkDir: String = localProperties.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: error("Android SDK path not found. Set sdk.dir in local.properties or ANDROID_HOME.")

tasks.register<Exec>("installDebugOnNexus5") {
    group = "install"
    description = "Builds and installs the debug APK on the Nexus 5 emulator (emulator-5554) only."
    dependsOn(":app:assembleDebug")
    commandLine(
        rootProject.file("$androidSdkDir/platform-tools/adb").absolutePath,
        "-s",
        "emulator-5554",
        "install",
        "-r",
        rootProject.file("app/build/outputs/apk/debug/app-debug.apk").absolutePath,
    )
}
