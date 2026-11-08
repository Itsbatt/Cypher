package com.cypher.music.ui.wrapped

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cypher.music.data.ThemePreferences
import com.cypher.music.data.WrappedStats
import com.cypher.music.ui.share.WrappedShareSheet
import com.cypher.music.ui.theme.AmbientArtworkColors
import com.cypher.music.ui.theme.CypherLavender
import com.cypher.music.ui.utils.AppHaptics

private const val SLIDE_COUNT = 4
private const val SLIDE_DURATION_MS = 7000

/**
 * Full-screen "stories" recap with a restrained, modern look: a near-black
 * canvas, one soft ambient glow, crisp typography and directional transitions.
 * Tap the left half to go back, the right half to advance; hold to pause.
 */
@Composable
fun WrappedScreen(
    stats: WrappedStats,
    year: Int,
    themePrefs: ThemePreferences,
    artworkColors: AmbientArtworkColors,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(onBack = onClose)

    var currentSlide by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    // A fresh Animatable per slide keeps pause/resume smooth and restarts at 0 on navigation.
    val progress = remember(currentSlide) { Animatable(0f) }

    fun goTo(index: Int) {
        when {
            index < 0 -> Unit // Already on the first slide.
            index >= SLIDE_COUNT -> onClose()
            index != currentSlide -> {
                AppHaptics.tabClick(themePrefs.hapticsEnabled)
                currentSlide = index
            }
        }
    }

    LaunchedEffect(currentSlide, paused, shareOpen) {
        if (paused || shareOpen) return@LaunchedEffect
        val remaining = ((1f - progress.value) * SLIDE_DURATION_MS).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
        goTo(currentSlide + 1)
    }

    val accent = artworkColors.accent.takeIf { it.alpha > 0.05f } ?: CypherLavender
    val secondary = artworkColors.secondary.takeIf { it.alpha > 0.05f } ?: accent

    val glow = rememberInfiniteTransition(label = "WrappedGlow")
    val drift by glow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WrappedGlowDrift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF15121B), Color(0xFF08070B))
                )
            )
    ) {
        // Two slow, low-opacity light pools keep the canvas alive without noise.
        Box(
            modifier = Modifier
                .size(520.dp)
                .offset(x = (-150).dp, y = (-210).dp + 90.dp * drift)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.30f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(480.dp)
                .offset(x = 170.dp, y = 130.dp - 70.dp * drift)
                .background(
                    Brush.radialGradient(
                        listOf(secondary.copy(alpha = 0.24f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        // Gesture layer: hold pauses, tap left rewinds, tap right advances.
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(currentSlide) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                tryAwaitRelease()
                                paused = false
                            },
                            onTap = { goTo(currentSlide - 1) }
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(currentSlide) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                tryAwaitRelease()
                                paused = false
                            },
                            onTap = { goTo(currentSlide + 1) }
                        )
                    }
            )
        }

        AnimatedContent(
            targetState = currentSlide,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 96.dp, bottom = 28.dp),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (
                    slideInHorizontally(animationSpec = tween(440)) { width ->
                        direction * width / 5
                    } + fadeIn(animationSpec = tween(320))
                    ) togetherWith (
                    slideOutHorizontally(animationSpec = tween(340)) { width ->
                        -direction * width / 8
                    } + fadeOut(animationSpec = tween(220))
                    )
            },
            label = "WrappedSlide"
        ) { slide ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (slide) {
                    0 -> WrappedMinutesSlide(stats = stats, year = year)
                    1 -> WrappedTopTracksSlide(stats = stats)
                    2 -> WrappedArtistsSlide(stats = stats)
                    else -> WrappedSummarySlide(stats = stats, onClose = onClose)
                }
            }
        }

        // Segmented story progress.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(SLIDE_COUNT) { index ->
                val fill = when {
                    index < currentSlide -> 1f
                    index == currentSlide -> progress.value
                    else -> 0f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f))
                ) {
                    if (fill > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fill)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.92f))
                        )
                    }
                }
            }
        }

        if (shareOpen) {
            WrappedShareSheet(
                stats = stats,
                accentColor = accent,
                onDismiss = { shareOpen = false }
            )
        }

        IconButton(
            onClick = { shareOpen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 30.dp, end = 54.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Compartir Wrapped",
                tint = Color.White.copy(alpha = 0.80f),
                modifier = Modifier.size(17.dp)
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 30.dp, end = 10.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cerrar Wrapped",
                tint = Color.White.copy(alpha = 0.80f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
