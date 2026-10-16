package com.lightxin.core.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class FifCookieJarTest {
    @Test fun identityCookiesDoNotReplaceSessionAndResetClearsBoth() {
        val jar = FifCookieJar()
        val url = "https://sttp.fifedu.com/".toHttpUrl()
        fun cookie(name: String) = Cookie.Builder().name(name).value("test").domain("fifedu.com").path("/").build()
        jar.saveFromResponse(url, listOf(cookie("SESSION")))
        jar.saveFromResponse(url, listOf(cookie("id")))
        assertEquals(setOf("SESSION", "id"), jar.loadForRequest(url).map { it.name }.toSet())
        jar.clear()
        assertTrue(jar.loadForRequest(url).isEmpty())
    }
}
