package com.example.mpod.data.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendConfig @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val address: String
        get() = preferences.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_ADDRESS

    val baseUrl: String
        get() = BackendAddressNormalizer.toBaseUrlOrNull(address) ?: DEFAULT_BASE_URL

    fun saveAddress(input: String): Result<String> {
        val normalized = BackendAddressNormalizer.toDisplayAddressOrNull(input)
            ?: return Result.failure(IllegalArgumentException("Enter a valid backend address, for example 192.168.0.222:5051."))

        preferences.edit()
            .putString(KEY_ADDRESS, normalized)
            .apply()

        return Result.success(normalized)
    }

    companion object {
        const val DEFAULT_ADDRESS = "192.168.0.222:5051"
        private const val DEFAULT_BASE_URL = "http://$DEFAULT_ADDRESS/"
        private const val PREFS_NAME = "mpod_backend_config"
        private const val KEY_ADDRESS = "backend_address"
    }
}
