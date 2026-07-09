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
            val urlString = key
            val cookieStrings = (value as? String)?.split(";") ?: emptyList()
            
            val url = urlString.toHttpUrlOrNull()
            if (url != null) {
                val cookies = cookieStrings.mapNotNull { Cookie.parse(url, it) }
                cookieStore[urlString] = cookies
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.host
        cookieStore[urlString] = cookies

        // Save to preferences
        val cookieString = cookies.joinToString(";") { it.toString() }
        preferences.edit().putString(urlString, cookieString).apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
}
