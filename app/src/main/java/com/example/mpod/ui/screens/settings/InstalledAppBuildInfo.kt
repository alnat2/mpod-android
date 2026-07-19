package com.example.mpod.ui.screens.settings

import com.example.mpod.BuildConfig

data class InstalledAppBuildInfo(
    val environment: String,
    val versionName: String,
    val versionCode: Int,
    val applicationId: String,
    val backendAddress: String
) {
    companion object {
        val Unknown = InstalledAppBuildInfo(
            environment = "Unknown",
            versionName = "unknown",
            versionCode = 0,
            applicationId = "unknown",
            backendAddress = "unknown"
        )
    }
}

internal fun currentInstalledAppBuildInfo(): InstalledAppBuildInfo = InstalledAppBuildInfo(
    environment = installedEnvironment(BuildConfig.APPLICATION_ID),
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
    applicationId = BuildConfig.APPLICATION_ID,
    backendAddress = BuildConfig.BACKEND_ADDRESS
)

internal fun installedEnvironment(applicationId: String): String =
    if (applicationId.endsWith(".test")) "Test" else "Production"
