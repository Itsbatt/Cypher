package com.cypher.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexColorTest {

    @Test
    fun `six digit values become opaque`() {
        assertEquals(0xFF9D7BB0L, HexColor.parse("#9D7BB0"))
        assertEquals(0xFF9D7BB0L, HexColor.parse("9d7bb0"))
        assertEquals(0xFF000000L, HexColor.parse("#000000"))
        assertEquals(0xFFFFFFFFL, HexColor.parse("#FFFFFF"))
    }

    @Test
    fun `eight digit values keep their alpha`() {
        assertEquals(0x80FF0000L, HexColor.parse("#80FF0000"))
        assertEquals(0x00FFFFFFL, HexColor.parse("00ffffff"))
    }

    @Test
    fun `invalid input is rejected`() {
        assertNull(HexColor.parse(""))
        assertNull(HexColor.parse("#12345"))
        assertNull(HexColor.parse("#1234567"))
        assertNull(HexColor.parse("#GGGGGG"))
        assertNull(HexColor.parse("#-12345"))
    }

    @Test
    fun `formatting round trips`() {
        val opaque = 0xFF9D7BB0L
        assertEquals("#9D7BB0", HexColor.toHex(opaque))
        assertEquals(opaque, HexColor.parse(HexColor.toHex(opaque)))
        val translucent = 0x80352244L
        assertEquals("#80352244", HexColor.toHex(translucent))
        assertEquals(translucent, HexColor.parse(HexColor.toHex(translucent)))
    }
}
