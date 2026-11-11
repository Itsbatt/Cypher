package com.cypher.music.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.cypher.music.data.room.CypherDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single entry point for everything account related.
 *
 * The UI never talks to Google or to the backend directly: it reads [state] and calls
 * [signIn], [signOut] or [syncNow]. Keeping this layer free of Compose means the same
 * orchestration can be reused by the future desktop client.
 */
class AccountRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionStore(appContext)
    private val gateway = GoogleAuthGateway(appContext)
    private val backend: SyncBackend = HttpSyncBackend()
    private val source = LocalSyncSource(appContext)
    private val db = CypherDatabase.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(
        AccountUiState(
            authConfigured = gateway.isConfigured,
            syncConfigured = backend.isConfigured
        )
    )
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    /** Bumped whenever remote data landed, so the UI reloads its Room-backed flows. */
    private val _remoteChanges = MutableStateFlow(0)
    val remoteChanges: StateFlow<Int> = _remoteChanges.asStateFlow()

    private val applier = SyncApplier(appContext) { onRemoteApplied() }
    private val engine = SyncEngine(appContext, backend, source, applier)

    private var lastAutoSyncAt: Long? = null
    private var consecutiveFailures = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        publishAccount()
        observeConnectivity()
        scope.launch { autoSync("arranque") }
    }

    // ---------------------------------------------------------------- auth

    suspend fun signIn(activityContext: Context): AuthResult {
        _state.update { it.copy(signingIn = true, message = null) }
        val result = gateway.signIn(activityContext)
        when (result) {
            is AuthResult.Success -> {
                session.saveSession(result.account, result.idToken)
                _state.update {
                    it.copy(account = result.account, signingIn = false, phase = SyncPhase.Syncing)
                }
                // Existing local data becomes the cloud baseline; nothing is deleted and
                // nothing is overwritten until the server answers with real records.
                val queued = source.enqueueFullSnapshot()
                Log.i(TAG, "Local snapshot queued for ${result.account.userId}: $queued records")
                val outcome = syncNow(manual = true)
                _state.update { it.copy(message = outcome.message()) }
            }
            AuthResult.Cancelled -> {
                _state.update { it.copy(signingIn = false, message = "Inicio de sesión cancelado.") }
            }
            AuthResult.NoNetwork -> {
                _state.update { it.copy(signingIn = false, message = "Sin conexión: inténtalo cuando vuelvas a estar en línea.") }
            }
            AuthResult.NotConfigured -> {
                _state.update {
                    it.copy(
                        signingIn = false,
                        message = "Falta configurar el inicio de sesión con Google en esta compilación."
                    )
                }
            }
            is AuthResult.Failed -> {
                _state.update { it.copy(signingIn = false, message = result.reason) }
            }
        }
        return result
    }

    suspend fun signOut() {
        gateway.signOut()
        session.clearSession()
        // Local data stays exactly as it is: signing out only stops syncing.
        consecutiveFailures = 0
        _state.value = AccountUiState(
            account = null,
            phase = SyncPhase.LocalOnly,
            authConfigured = gateway.isConfigured,
            syncConfigured = backend.isConfigured,
            pendingChanges = pendingCount(),
            message = "Sesión cerrada. Tus datos siguen en este dispositivo."
        )
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    // ---------------------------------------------------------------- sync

    suspend fun syncNow(manual: Boolean = true): SyncOutcome {
        val account = session.account() ?: return SyncOutcome.NotSignedIn
        if (!backend.isConfigured) return SyncOutcome.NotConfigured
        val token = session.idToken()
        if (token.isNullOrBlank()) return SyncOutcome.NotSignedIn

        syncMutex.withLock {
            if (!isOnline()) {
                _state.update { it.copy(phase = SyncPhase.Offline, pendingChanges = pendingCount()) }
                return SyncOutcome.Offline
            }
            _state.update { it.copy(phase = SyncPhase.Syncing, message = null) }
            val outcome = engine.run(account.userId, token) { pending ->
                _state.update { it.copy(pendingChanges = pending) }
            }
            applyOutcome(outcome)
            return outcome
        }
    }

    private fun applyOutcome(outcome: SyncOutcome) {
        when (outcome) {
            is SyncOutcome.Success -> {
                consecutiveFailures = 0
                _state.update {
                    it.copy(
                        phase = SyncPhase.Synced(outcome.at),
                        lastSyncedAt = outcome.at,
                        pendingChanges = pendingCount()
                    )
                }
            }
            SyncOutcome.Offline -> {
                consecutiveFailures += 1
                _state.update { it.copy(phase = SyncPhase.Offline, pendingChanges = pendingCount()) }
            }
            SyncOutcome.NotSignedIn -> {
                _state.update { it.copy(phase = SyncPhase.LocalOnly) }
            }
            SyncOutcome.NotConfigured -> {
                _state.update { it.copy(phase = SyncPhase.Failed("Sincronización no configurada")) }
            }
            is SyncOutcome.Failed -> {
                consecutiveFailures += 1
                _state.update {
                    it.copy(phase = SyncPhase.Failed(outcome.message), pendingChanges = pendingCount())
                }
                Log.w(TAG, "Sync failed: ${outcome.message}")
            }
        }
    }

    /** Automatic pass: throttled, silent, never throws in the caller's face. */
    private suspend fun autoSync(reason: String) {
        val account = session.account() ?: return
        if (!backend.isConfigured || !isOnline()) return
        val now = System.currentTimeMillis()
        if (!SyncPolicy.canAutoSync(lastAutoSyncAt, now)) return
        lastAutoSyncAt = now
        Log.i(TAG, "Auto sync ($reason) for ${account.userId}")
        if (pendingCount() == 0 && _state.value.lastSyncedAt != null) {
            // Still pull: another device may have added something.
        }
        syncNow(manual = false)
    }

    fun requestAutoSync(reason: String) {
        scope.launch { autoSync(reason) }
    }

    // ------------------------------------------------------------- linking

    /** Room is queried off the main thread: a main-thread read is refused by Room. */
    suspend fun shouldOfferLink(): Boolean = withContext(Dispatchers.IO) {
        val account = _state.value.account
        val localData = LocalSyncSource.hasLocalData(appContext)
        val offer = SyncPolicy.shouldOfferLink(
            // Any real local footprint counts: playlists, favorites, downloads or the
            // listening rollup that feeds Wrapped.
            hasLocalData = localData,
            isSignedIn = account != null,
            authConfigured = gateway.isConfigured,
            lastOfferAt = session.lastOfferAt(),
            lastDismissedAt = session.lastDismissedAt(),
            now = System.currentTimeMillis()
        )
        if (offer) session.markOfferShown()
        return@withContext offer
    }

    fun dismissLinkOffer() {
        session.markDismissed()
    }

    private fun playlistsCount(): Int = runCatching {
        db.playlistDao().getAllPlaylists().size
    }.getOrDefault(0)

    private fun favoritesCount(): Int = runCatching {
        db.trackDao().getFavoriteTrackIds().size
    }.getOrDefault(0)

    // ------------------------------------------------------------ plumbing

    private fun publishAccount() {
        // Never let the account layer take the app down: if Room cannot be read the
        // UI simply stays in local-only mode.
        val account = runCatching { session.account() }.getOrNull()
        val state = runCatching { db.syncStateDao().get(account?.userId.orEmpty()) }
            .onFailure { Log.w(TAG, "No sync state available: ${it.message}") }
            .getOrNull()
        _state.update {
            it.copy(
                account = account,
                lastSyncedAt = state?.lastSyncedAt?.takeIf { value -> value > 0L },
                pendingChanges = pendingCount(),
                authConfigured = gateway.isConfigured,
                syncConfigured = backend.isConfigured,
                phase = if (account == null) SyncPhase.LocalOnly else it.phase
            )
        }
    }

    private fun pendingCount(): Int = SyncQueue.pendingCount(appContext)

    private suspend fun onRemoteApplied() {
        _remoteChanges.update { it + 1 }
    }

    fun isOnline(): Boolean = runCatching {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    private fun observeConnectivity() {
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { autoSync("red disponible") }
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }.onFailure { Log.w(TAG, "No network callback: ${it.message}") }
    }

    private fun SyncOutcome.message(): String? = when (this) {
        is SyncOutcome.Success -> "Cuenta conectada y datos sincronizados."
        SyncOutcome.Offline -> "Cuenta conectada. Se sincronizará cuando haya conexión."
        SyncOutcome.NotConfigured -> "Cuenta conectada, pero falta el servidor de sincronización."
        SyncOutcome.NotSignedIn -> null
        is SyncOutcome.Failed -> message
    }

    private companion object {
        const val TAG = "AccountRepository"
    }
}
