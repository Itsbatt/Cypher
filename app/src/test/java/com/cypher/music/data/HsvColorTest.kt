package com.cypher.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HsvColorTest {

    @Test
    fun `primary colours convert to the expected hue`() {
        assertEquals(0f, HsvColor.fromArgb(0xFFFF0000).hue, 0.01f)
        assertEquals(120f, HsvColor.fromArgb(0xFF00FF00).hue, 0.01f)
        assertEquals(240f, HsvColor.fromArgb(0xFF0000FF).hue, 0.01f)
    }

    @Test
    fun `greys have no saturation`() {
        val white = HsvColor.fromArgb(0xFFFFFFFF)
        val grey = HsvColor.fromArgb(0xFF808080)
        val black = HsvColor.fromArgb(0xFF000000)
        assertEquals(0f, white.saturation, 0.001f)
        assertEquals(0f, grey.saturation, 0.001f)
        assertEquals(0f, black.value, 0.001f)
        assertEquals(0f, black.saturation, 0.001f)
    }

    @Test
    fun `argb round trips through hsv`() {
        val samples = listOf(0xFF9D7BB0L, 0xFF352244L, 0xFF00E5FFL, 0xFFFFB300L, 0xFF6A1B9AL)
        for (argb in samples) {
            val restored = HsvColor.fromArgb(argb).toArgb()
            assertEquals("round trip for ${HexColor.toHex(argb)}", argb, restored)
        }
    }

    @Test
    fun `alpha is preserved`() {
        val translucent = HsvColor.fromArgb(0x809D7BB0L).toArgb(alpha = 0x80)
        assertEquals(0x809D7BB0L, translucent)
    }
}
