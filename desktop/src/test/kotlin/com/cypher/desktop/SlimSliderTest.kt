package com.cypher.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cypher.desktop.ui.SlimSlider
import kotlin.test.Test
import kotlin.test.assertTrue

class SlimSliderTest {

    @Test
    fun slimSliderHandlesZeroAndSmallWidthsWithoutCrash() {
        // Test zero width, small width (smaller than 2 * thumbRadius = 10dp), and normal width
        val widths = listOf(0.dp, 2.dp, 5.dp, 8.dp, 10.dp, 100.dp)

        for (w in widths) {
            val scene = ImageComposeScene(width = 200, height = 50, density = Density(1f)) {
                Box(modifier = Modifier.width(w)) {
                    SlimSlider(
                        value = 0.5f,
                        onValueChange = {},
                        color = Color.Magenta
                    )
                }
            }
            try {
                scene.render()
            } finally {
                scene.close()
            }
        }
        assertTrue(true, "All SlimSlider layouts rendered without IllegalArgumentException")
    }
}
