package com.cypher.music.update

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UpdateUiState(
    val visible: Boolean = false,
    val release: ReleaseInfo? = null,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = UpdatePreferences(application)
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var verifiedFile: File? = null
    private var verifiedVersion: String? = null

    init {
        checkForUpdates()
    }

    private fun installedVersion(): AppVersion? = runCatching {
        val app = getApplication<Application>()
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        AppVersion.parse(info.versionName)
    }.getOrNull()

    fun checkForUpdates(force: Boolean = false) {
        if (_state.value.checking || _state.value.downloading) return
        val now = System.currentTimeMillis()
        val recentlyChecked = now >= preferences.lastCheckAt &&
            now - preferences.lastCheckAt < CHECK_COOLDOWN_MS
        if (!force && recentlyChecked) return

        val installed = installedVersion() ?: return
        _state.update { it.copy(checking = true) }

        viewModelScope.launch {
            val result = GitHubUpdateClient.fetchLatest()
            preferences.lastCheckAt = System.currentTimeMillis()

            result.onSuccess { latest ->
                if (latest == null || latest.version <= installed) {
                    Log.i(
                        TAG,
                        "Sin actualizaciones: instalada=$installed, última=${latest?.versionName ?: "ninguna"}"
                    )
                    _state.update { it.copy(checking = false, visible = false, release = null) }
                    return@launch
                }

                val dismissed = preferences.dismissedVersion == latest.versionName
                Log.i(TAG, "Actualización disponible ${latest.versionName} (descartada=$dismissed)")
                _state.update {
                    it.copy(
                        checking = false,
                        release = latest,
                        visible = !dismissed,
                        error = null
                    )
                }
            }.onFailure { error ->
                Log.w(TAG, "No se pudo comprobar la actualización: ${error.message}")
                _state.update { it.copy(checking = false) }
            }
        }
    }

    fun dismiss() {
        val release = _state.value.release ?: return
        preferences.dismissedVersion = release.versionName
        _state.update { it.copy(visible = false) }
    }

    fun downloadAndInstall() {
        val release = _state.value.release ?: return
        if (_state.value.downloading) return

        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val cached = verifiedFile
                if (cached != null && cached.exists() && verifiedVersion == release.versionName) {
                    install(app, cached)
                    return@launch
                }

                val asset = UpdateAssetSelector.select(release.assets, Build.SUPPORTED_ABIS.toList())
                if (asset == null) {
                    fail("Esta release no incluye un APK compatible con tu dispositivo.")
                    return@launch
                }

                _state.update { it.copy(downloading = true, progress = 0f, error = null) }
                val checksum = runCatching { UpdateInstaller.fetchChecksum(release, asset) }.getOrNull()
                val targetDir = File(app.cacheDir, "updates")
                val downloaded = UpdateInstaller.download(asset, checksum, targetDir) { progress ->
                    _state.update { it.copy(progress = progress) }
                }.getOrElse { error ->
                    fail(error.message ?: "No se pudo descargar la actualización.")
                    return@launch
                }

                val problem = withContext(Dispatchers.IO) {
                    UpdateInstaller.verifyApk(app, downloaded, release.version, checksum)
                }
                if (problem != null) {
                    downloaded.delete()
                    fail(problem)
                    return@launch
                }

                verifiedFile = downloaded
                verifiedVersion = release.versionName
                _state.update { it.copy(downloading = false, progress = 1f) }
                install(app, downloaded)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(error.message ?: "Error inesperado al actualizar.")
            }
        }
    }

    private fun install(app: Application, file: File) {
        when (val result = UpdateInstaller.install(app, file)) {
            InstallResult.Started -> _state.update { it.copy(visible = false, error = null) }
            InstallResult.NeedsPermission -> _state.update {
                it.copy(error = "Activa «Instalar apps desconocidas» para Cypher y vuelve a pulsar Actualizar.")
            }
            is InstallResult.Failed -> fail(result.message)
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(downloading = false, error = message) }
    }

    private companion object {
        const val TAG = "UpdateViewModel"
        const val CHECK_COOLDOWN_MS = 15 * 60 * 1000L
    }
}
