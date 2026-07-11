package com.example.mpod.data.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

class PersistentCookieJar(context: Context) : CookieJar {
    private val preferences: SharedPreferences = context.getSharedPreferences("CookiePrefs", Context.MODE_PRIVATE)
    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    init {
        // Load cookies from preferences
        val allEntries = preferences.all
        for ((key, value) in allEntries) {
            val cookieStrings = (value as? String)?.lineSequence()?.filter { it.isNotBlank() }?.toList() ?: emptyList()
            val url = key.toCookieUrlOrNull()
            if (url != null) {
                val cookies = cookieStrings.mapNotNull { Cookie.parse(url, it) }
                    .filter { it.expiresAt > System.currentTimeMillis() }
                if (cookies.isNotEmpty()) {
                    cookieStore[key] = cookies
                }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.storageKey()
        val freshCookies = cookies.filter { it.expiresAt > System.currentTimeMillis() }
        cookieStore[key] = freshCookies

        val cookieString = freshCookies.joinToString("\n") { it.toString() }
        preferences.edit().putString(key, cookieString).apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore.values
            .flatten()
            .filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
    }

    private fun HttpUrl.storageKey(): String {
        return "$scheme://$host:$port/"
    }

    private fun String.toCookieUrlOrNull(): HttpUrl? {
        val candidate = when {
            startsWith("http://") || startsWith("https://") -> this
            else -> "http://$this/"
        }
        return candidate.toHttpUrlOrNull()
    }
}
