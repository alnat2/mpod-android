import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.kapt)
}

fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseSigningKeys = listOf(
    "MPOD_RELEASE_STORE_FILE",
    "MPOD_RELEASE_STORE_PASSWORD",
    "MPOD_RELEASE_KEY_ALIAS",
    "MPOD_RELEASE_KEY_PASSWORD",
)
val releaseSigning = releaseSigningKeys.associateWith(::env)
val checkedInDebugCertificateSha256 = "61f0b1bb4485fcf4333e005e1adb43115340eb6b63b8f378cce5319430a4d012"

configurations.all {
    resolutionStrategy.eachDependency {
        when (requested.group) {
            "androidx.lifecycle" -> useVersion("2.8.7")
            "androidx.activity" -> useVersion("1.9.3")
            "androidx.core" -> useVersion("1.13.1")
        }
    }
}

android {
    namespace = "com.example.mpod"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prod.mpod"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "1.0.19"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseSigning["MPOD_RELEASE_STORE_FILE"]?.let(::file)
            storePassword = releaseSigning["MPOD_RELEASE_STORE_PASSWORD"]
            keyAlias = releaseSigning["MPOD_RELEASE_KEY_ALIAS"]
            keyPassword = releaseSigning["MPOD_RELEASE_KEY_PASSWORD"]
        }
        create("qaRelease") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".test"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        create("qaRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".signingtest"
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("qaRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Checks that a distributable release has a complete private signing configuration."
    doLast {
        val missing = releaseSigningKeys.filter { releaseSigning[it] == null }
        check(missing.isEmpty()) {
            "Release signing is incomplete. Set ${missing.joinToString()} outside Git, " +
                "or build assembleQaRelease for a clearly identified test APK."
        }
        val keystore = file(releaseSigning.getValue("MPOD_RELEASE_STORE_FILE")!!)
        check(keystore.isFile) { "MPOD_RELEASE_STORE_FILE must point to an existing keystore file." }
        check(keystore.canonicalFile != file("debug.keystore").canonicalFile) {
            "The checked-in debug.keystore cannot sign a distributable release."
        }
        val store = KeyStore.getInstance(
            keystore,
            releaseSigning.getValue("MPOD_RELEASE_STORE_PASSWORD")!!.toCharArray(),
        )
        val certificate = store.getCertificate(releaseSigning.getValue("MPOD_RELEASE_KEY_ALIAS")!!)
            ?: error("MPOD_RELEASE_KEY_ALIAS was not found in the release keystore.")
        val certificateSha256 = MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        check(certificateSha256 != checkedInDebugCertificateSha256) {
            "The checked-in debug certificate cannot sign a distributable release."
        }
    }
}

tasks.matching {
    it.name in setOf("assembleRelease", "packageRelease", "bundleRelease", "signReleaseBundle")
}.configureEach {
    dependsOn(validateReleaseSigning)
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    
    // Direct RSS, artwork and audio requests
    implementation(libs.okhttp)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // DataStore & WorkManager
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    // Navigation & Coil
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    // Match resolved core 1.7.3; Main dispatcher control for local ViewModel tests only.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kxml2)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.mockwebserver)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
