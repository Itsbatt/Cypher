package com.cypher.music.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    CRITICAL
}

data class StreamingHealth(
    val status: HealthStatus = HealthStatus.HEALTHY,
    val consecutiveFailures: Int = 0,
    val lastErrorTimestamp: Long = 0L,
    val lastErrorCode: Int? = null,
    val lastErrorMessage: String? = null,
    val isSabrDetected: Boolean = false,
    val activeFallbackTier: Int = 1
)

/**
 * Passive Telemetry & Resilience Monitor for InnerTube / yt-dlp streaming engines.
 * Detects structural breakages (HTTP 403, SABR protocol locks, format changes)
 * in real-time without user-facing disruption.
 */
object StreamingTelemetry {
    private const val TAG = "StreamingTelemetry"

    private val _health = MutableStateFlow(StreamingHealth())
    val health: StateFlow<StreamingHealth> = _health.asStateFlow()

    fun recordSuccess(tier: Int = 1) {
        _health.update {
            it.copy(
                status = HealthStatus.HEALTHY,
                consecutiveFailures = 0,
                isSabrDetected = false,
                activeFallbackTier = tier
            )
        }
        Log.d(TAG, "Streaming health recorded: HEALTHY via Tier $tier")
    }

    fun recordError(errorCode: Int, message: String?, endpoint: String = "unknown") {
        val msg = message ?: "Unknown streaming error"
        val isSabr = errorCode == 403 || msg.contains("403") || msg.contains("SABR", ignoreCase = true)

        _health.update { current ->
            val failures = current.consecutiveFailures + 1
            val status = when {
                failures >= 4 -> HealthStatus.CRITICAL
                failures >= 1 -> HealthStatus.DEGRADED
                else -> HealthStatus.HEALTHY
            }
            current.copy(
                status = status,
                consecutiveFailures = failures,
                lastErrorTimestamp = System.currentTimeMillis(),
                lastErrorCode = errorCode,
                lastErrorMessage = "[$endpoint] $msg",
                isSabrDetected = isSabr
            )
        }

        Log.w(TAG, "Streaming anomaly detected: code=$errorCode, endpoint=$endpoint, msg=$msg. Consecutive failures=${_health.value.consecutiveFailures}")
    }

    fun getSuggestedFallbackTier(): Int {
        val current = _health.value
        return when {
            current.consecutiveFailures >= 3 -> 3
            current.consecutiveFailures >= 1 || current.isSabrDetected -> 2
            else -> 1
        }
    }
}
