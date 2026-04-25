package com.lightxin.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FifSessionManagerTokenParsingTest {

    @Test
    fun `extract memberId from fif jwt token`() {
        val token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJyZWFsTmFtZSI6IuS9leWuh-e_lCIsInNjaG9vbElkIjoiMjgxMTAwMDIyNjAwMDAwMTY3OCIsImlzcyI6ImZpZmFjIiwiaWQiOiIzNDAyMDMwMDAwMDAxMDExNTkyIiwiZXhwIjoxNzc3NDYyNDQyLCJ1c2VybmFtZSI6ImFpaXQzMjMzMDMyMjM1IiwibWVtYmVySWQiOiI5NWY3NzdkZTIzNGZkNDI4YjM5ZDg0ZDY2NTFiODM5NyJ9.AqA1SLHMfeqopZUv8-jse87Ww3bDuk8tdy3MErY6WLM"

        assertEquals(
            "95f777de234fd428b39d84d6651b8397",
            extractMemberUserIdFromFifToken(token),
        )
    }

    @Test
    fun `return null when token payload has no memberId`() {
        val token = "header.eyJ1c2VybmFtZSI6ImFpaXQifQ.signature"

        assertNull(extractMemberUserIdFromFifToken(token))
    }
}
