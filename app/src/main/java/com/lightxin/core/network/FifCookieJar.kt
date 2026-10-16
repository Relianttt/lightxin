package com.lightxin.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FifCookieJar @Inject constructor() : CookieJar {
    private val cookies = linkedMapOf<Triple<String, String, String>, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { this.cookies[Triple(it.domain, it.path, it.name)] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.entries.removeAll { it.value.expiresAt <= System.currentTimeMillis() }
        return cookies.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() = cookies.clear()
}
