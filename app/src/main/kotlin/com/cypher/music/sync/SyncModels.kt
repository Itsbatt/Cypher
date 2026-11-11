package com.cypher.music.sync

/**
 * Device-independent description of a synced entity.
 *
 * Everything here is plain data with string keys so the same payload can travel
 * between the Android app and the future desktop build: no Android types, no
 * device-local paths, no cached URIs.
 */
enum class SyncEntityKind {
    PROFILE,
    PLAYLIST,
    FAVORITE,
    PREFERENCE,
    STATS
}

/**
 * One versioned row in the cloud.
 *
 * [updatedAt] is the authority for conflicts (last writer wins, with a
 * deterministic tie-break) and [deleted] is a tombstone: deletes must propagate,
 * so they are records too instead of disappearing.
 */
data class RemoteRecord(
    val kind: SyncEntityKind,
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val payload: Map<String, String> = emptyMap()
) {
    /** Stable key inside the outbox and inside merge decisions. */
    val key: String get() = "${kind.name}:$id"
}

/** A page returned by the backend. */
data class PullPage(
    val records: List<RemoteRecord> = emptyList(),
    val cursor: String? = null,
    val hasMore: Boolean = false
)

/** Outcome of a push: the ids the cloud accepted. */
data class PushResult(val accepted: List<String> = emptyList())

/** The Google account currently linked, as far as this device is concerned. */
data class CloudAccount(
    val userId: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null
)

/** High level state shown in Settings. */
sealed interface SyncPhase {
    /** No Google account linked: Cypher keeps working locally. */
    data object LocalOnly : SyncPhase

    /** Configured but not reachable yet (no network or backend disabled). */
    data object Offline : SyncPhase

    data object Syncing : SyncPhase

    data class Synced(val at: Long) : SyncPhase

    data class Failed(val message: String, val at: Long = System.currentTimeMillis()) : SyncPhase
}

/** Everything the account section needs to render, in one snapshot. */
data class AccountUiState(
    val account: CloudAccount? = null,
    val phase: SyncPhase = SyncPhase.LocalOnly,
    val lastSyncedAt: Long? = null,
    val pendingChanges: Int = 0,
    val authConfigured: Boolean = false,
    val syncConfigured: Boolean = false,
    val signingIn: Boolean = false,
    val message: String? = null
) {
    val isSignedIn: Boolean get() = account != null
}

/** Result of a sign-in attempt, mapped to user-visible copy by the UI layer. */
sealed interface AuthResult {
    data class Success(val account: CloudAccount, val idToken: String) : AuthResult
    data object Cancelled : AuthResult
    data object NotConfigured : AuthResult
    data object NoNetwork : AuthResult
    data class Failed(val reason: String) : AuthResult
}

/** Result of a full sync pass. */
sealed interface SyncOutcome {
    data class Success(val pulled: Int, val pushed: Int, val at: Long) : SyncOutcome
    data object NotSignedIn : SyncOutcome
    data object NotConfigured : SyncOutcome
    data object Offline : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}
