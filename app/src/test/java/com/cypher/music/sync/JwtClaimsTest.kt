package com.cypher.music.sync

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class JwtClaimsTest {

    private fun token(payload: String): String {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "header.$encoded.signature"
    }

    @Test
    fun `reads the email and subject of a google id token`() {
        val idToken = token("""{"email":"persona@ejemplo.com","sub":"1234567890"}""")
        assertEquals("persona@ejemplo.com", JwtClaims.email(idToken))
        assertEquals("1234567890", JwtClaims.subject(idToken))
    }

    @Test
    fun `returns null instead of throwing on malformed tokens`() {
        assertNull(JwtClaims.email(null))
        assertNull(JwtClaims.email("no-es-un-jwt"))
        assertNull(JwtClaims.email("header.%%%%.signature"))
    }

    @Test
    fun `decodes url safe segments with missing padding`() {
        val payload = """{"email":"ñandú@ejemplo.com","n":"a+b/c"}"""
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        assertFalse(encoded.contains("="))
        assertEquals(payload, JwtClaims.decodeBase64Url(encoded))
    }
}
