package com.cypher.music.ui.components

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cypher.music.ui.theme.IosDarkSurface
import com.cypher.music.ui.utils.AppHaptics
import java.util.Locale

/**
 * Luxurious in-app Voice Search Modal for Cypher.
 * Replaces the default external Google dialog with fluid soundwave animations,
 * reactive real-time transcript streaming, and glowing lavender orbs.
 */
@Composable
fun CypherVoiceSearchModal(
    onDismiss: () -> Unit,
    onSpeechResult: (String) -> Unit,
    hapticsEnabled: Boolean = true
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableFloatStateOf(0f) }
    var transcriptText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val lavenderAccent = Color(0xFF9D7BB0)
    val purpleAccent = Color(0xFF8E54E9)
    val pinkAccent = Color(0xFFE040FB)

    // SpeechRecognizer lifecycle & listener management
    DisposableEffect(retryTrigger) {
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (_: Exception) {
            null
        }

        if (recognizer == null || !SpeechRecognizer.isRecognitionAvailable(context)) {
            errorMessage = "El servicio de reconocimiento de voz no está disponible en este dispositivo."
            return@DisposableEffect onDispose {}
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                errorMessage = null
                AppHaptics.actionClick(hapticsEnabled)
            }

            override fun onBeginningOfSpeech() {
                isSpeaking = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Normalize rmsdB (-2 to ~10+) to [0.0, 1.0]
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                audioLevel = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isSpeaking = false
            }

            override fun onError(error: Int) {
                isListening = false
                isSpeaking = false
                audioLevel = 0f
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó ninguna voz"
                    SpeechRecognizer.ERROR_AUDIO -> "Error de grabación de audio"
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Problema de red o conectividad"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono requerido"
                    else -> "No se pudo transcribir la voz"
                }
                errorMessage = msg
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                isSpeaking = false
                audioLevel = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalPhrase = matches?.firstOrNull()?.trim()
                if (!finalPhrase.isNullOrBlank()) {
                    transcriptText = finalPhrase
                    AppHaptics.actionClick(hapticsEnabled)
                    onSpeechResult(finalPhrase)
                    onDismiss()
                } else {
                    errorMessage = "No se reconoció ninguna frase"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialPhrase = partialMatches?.firstOrNull()?.trim()
                if (!partialPhrase.isNullOrBlank()) {
                    transcriptText = partialPhrase
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }

        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            errorMessage = "No se pudo iniciar el servicio de voz"
        }

        onDispose {
            try {
                recognizer.stopListening()
                recognizer.cancel()
                recognizer.destroy()
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            val cardShape = RoundedCornerShape(32.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(cardShape)
                    .background(IosDarkSurface.copy(alpha = 0.94f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = cardShape
                    )
                    .shadow(elevation = 20.dp, shape = cardShape)
                    .clickable(enabled = false) {} // Prevent click-through dismiss
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                // Top-right close button
                IconButton(
                    onClick = {
                        AppHaptics.actionClick(hapticsEnabled)
                        onDismiss()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Búsqueda por Voz",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Pulsating Glowing Soundwave Orb
                    VoiceWaveVisualizer(
                        audioLevel = audioLevel,
                        isListening = isListening,
                        accentColor = lavenderAccent
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Dancing Equalizer Wave Bars
                    AudioBarsIndicator(
                        audioLevel = audioLevel,
                        isListening = isListening
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Real-Time Transcript or Status Message
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFF5252),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        ElevatedButton(
                            onClick = {
                                AppHaptics.actionClick(hapticsEnabled)
                                transcriptText = ""
                                errorMessage = null
                                retryTrigger++
                            },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = purpleAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reintentar",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        if (transcriptText.isNotBlank()) {
                            Text(
                                text = "«$transcriptText»",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        } else {
                            Text(
                                text = if (isSpeaking) "Escuchando..." else "Habla ahora...",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Di el nombre de una canción, artista o lista",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated concentric pulsating radial halos reacting to ambient sound and voice level.
 */
@Composable
private fun VoiceWaveVisualizer(
    audioLevel: Float,
    isListening: Boolean,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    val animatedLevel by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = tween(90, easing = LinearEasing),
        label = "animatedLevel"
    )

    val effectiveScale = if (isListening) (1f + animatedLevel * 0.45f) * idlePulse else 1f

    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 3.4f

            // Outer ethereal diffused glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.35f * (animatedLevel + 0.35f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.8f * effectiveScale
                ),
                radius = baseRadius * 1.8f * effectiveScale,
                center = center
            )

            // Mid vibrant ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8E54E9).copy(alpha = 0.45f * (animatedLevel + 0.4f)),
                        accentColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.35f * effectiveScale
                ),
                radius = baseRadius * 1.35f * effectiveScale,
                center = center
            )

            // Delicate border ripple
            drawCircle(
                color = accentColor.copy(alpha = 0.5f + animatedLevel * 0.4f),
                radius = baseRadius * effectiveScale,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Inner glowing core orb with mic icon
        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(if (isListening) idlePulse else 1f)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF8E54E9),
                            Color(0xFF9D7BB0),
                            Color(0xFFE040FB)
                        )
                    )
                )
                .shadow(12.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Dancing audio equalizer bars responding organically to speech decibel levels.
 */
@Composable
private fun AudioBarsIndicator(
    audioLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 7
    val infiniteTransition = rememberInfiniteTransition(label = "bars")

    val basePulse by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "barsPulse"
    )

    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barScales = listOf(0.45f, 0.75f, 1.0f, 0.85f, 0.95f, 0.65f, 0.4f)
        for (i in 0 until barCount) {
            val scale = barScales[i]
            val activeHeight = if (isListening) {
                val factor = (audioLevel * 1.4f + basePulse * 0.35f).coerceIn(0.2f, 1.0f)
                (scale * factor * 26).coerceIn(5f, 26f)
            } else {
                5f
            }

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(activeHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFE040FB),
                                Color(0xFF9D7BB0),
                                Color(0xFF8E54E9)
                            )
                        )
                    )
            )
        }
    }
}
