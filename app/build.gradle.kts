plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.example.mpod"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.prod.mpod"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "1.0.17"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val releaseStoreFile = env("MPOD_RELEASE_STORE_FILE")
            if (releaseStoreFile == null) {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                storeFile = file(releaseStoreFile)
                storePassword = env("MPOD_RELEASE_STORE_PASSWORD")
                    ?: error("MPOD_RELEASE_STORE_PASSWORD is required when MPOD_RELEASE_STORE_FILE is set.")
                keyAlias = env("MPOD_RELEASE_KEY_ALIAS")
                    ?: error("MPOD_RELEASE_KEY_ALIAS is required when MPOD_RELEASE_STORE_FILE is set.")
                keyPassword = env("MPOD_RELEASE_KEY_PASSWORD")
                    ?: error("MPOD_RELEASE_KEY_PASSWORD is required when MPOD_RELEASE_STORE_FILE is set.")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".test"
            buildConfigField("String", "BACKEND_SCHEME", "\"http\"")
            buildConfigField("String", "BACKEND_ADDRESS", "\"192.168.0.222:5051\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "BACKEND_SCHEME", "\"http\"")
            buildConfigField("String", "BACKEND_ADDRESS", "\"192.168.0.222:5050\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
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
    
    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore & WorkManager
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    // Navigation & Coil
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.mockwebserver)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
