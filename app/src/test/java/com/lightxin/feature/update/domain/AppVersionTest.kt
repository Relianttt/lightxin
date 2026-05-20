package com.lightxin.feature.update.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parse valid tag with v prefix`() {
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("v1.2.0"))
    }

    @Test
    fun `parse valid tag without v prefix`() {
        assertEquals(AppVersion(1, 1, 0), AppVersion.parse("1.1.0"))
    }

    @Test
    fun `parse two segment tag returns null`() {
        assertNull(AppVersion.parse("v1.1"))
    }

    @Test
    fun `parse prerelease tag returns null`() {
        assertNull(AppVersion.parse("v1.1.0-beta"))
    }

    @Test
    fun `parse empty string returns null`() {
        assertNull(AppVersion.parse(""))
    }

    @Test
    fun `parse tag with spaces is trimmed`() {
        assertEquals(AppVersion(1, 0, 0), AppVersion.parse(" v1.0.0 "))
    }

    @Test
    fun `compare major version`() {
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 9, 9))
    }

    @Test
    fun `compare minor version`() {
        assertTrue(AppVersion(1, 2, 0) > AppVersion(1, 1, 0))
    }

    @Test
    fun `compare patch version`() {
        assertTrue(AppVersion(1, 1, 1) > AppVersion(1, 1, 0))
    }

    @Test
    fun `equal versions`() {
        assertEquals(0, AppVersion(1, 1, 0).compareTo(AppVersion(1, 1, 0)))
    }

    @Test
    fun `toVersionName formats correctly`() {
        assertEquals("1.2.3", AppVersion(1, 2, 3).toVersionName())
    }
}
