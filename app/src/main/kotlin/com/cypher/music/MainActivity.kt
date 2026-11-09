package com.cypher.music

import android.Manifest
import android.app.AlarmManager
import android.app.SearchManager
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.cypher.music.ui.components.CypherTab
import com.cypher.music.ui.main.MainViewModel
import com.cypher.music.ui.theme.CypherTheme
import com.cypher.music.wrapped.WrappedScheduler

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // The audio permission is READ_MEDIA_AUDIO on Android 13+ and the legacy
        // READ_EXTERNAL_STORAGE below it. Either grant refreshes the local library.
        val granted = permissions[Manifest.permission.READ_MEDIA_AUDIO] == true ||
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
            ContextCompat.checkSelfPermission(
                this,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.refreshLocalStorage()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestRequiredPermissions()
        requestExactAlarmAccessIfNeeded()
        handleIncomingIntent(intent)

        setContent {
            CypherTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-arms the Wrapped alarm: upgrades an inexact fallback to an exact alarm
        // as soon as the user grants "Alarms & reminders".
        WrappedScheduler.schedule(this)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // Deep link from the Wrapped notification: cypher://wrapped?year=2026
        val isWrappedLink = intent.action == WrappedScheduler.ACTION_OPEN_WRAPPED ||
            (intent.data?.scheme == WrappedScheduler.DEEP_LINK_SCHEME &&
                intent.data?.host == WrappedScheduler.DEEP_LINK_HOST)
        if (isWrappedLink) {
            val year = intent.getIntExtra(WrappedScheduler.EXTRA_YEAR, WrappedScheduler.YEAR)
            viewModel.openWrapped(year)
            return
        }

        val action = intent.action ?: return

        // Assistant, Bixby and the system search bar send the query here. The intent
        // was declared in the manifest but never handled, so voice search did nothing.
        if (action == Intent.ACTION_SEARCH || action == "android.media.action.MEDIA_PLAY_FROM_SEARCH") {
            val query = intent.getStringExtra(SearchManager.QUERY)
                ?: intent.getStringExtra("query")
            if (!query.isNullOrBlank()) {
                viewModel.selectTab(CypherTab.SEARCH)
                viewModel.onSearchQueryChange(query)
            }
            return
        }

        if (action == Intent.ACTION_VIEW || action == "android.intent.action.MUSIC_PLAYER") {
            val dataUri = intent.data ?: return
            // Only audio handed over as a content or file URI is playable. Anything
            // else (http, custom schemes, relative paths) is ignored instead of
            // being resolved against the app's own storage.
            if (dataUri.scheme !in ACCEPTED_AUDIO_SCHEMES) {
                Log.w(TAG, "Ignoring audio intent with unsupported scheme: ${dataUri.scheme}")
                return
            }
            val displayName = queryDisplayName(dataUri) ?: "Canción de audio"
            viewModel.playFromUri(dataUri, displayName)
        }
    }

    private companion object {
        private const val TAG = "MainActivity"

        /** Audio is only accepted as a content:// or file:// URI. */
        private val ACCEPTED_AUDIO_SCHEMES = setOf(
            ContentResolver.SCHEME_CONTENT,
            ContentResolver.SCHEME_FILE
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                uri.lastPathSegment
            }
        }
    }

    /**
     * SCHEDULE_EXACT_ALARM is a special access, not a runtime permission. Ask once
     * through system settings so the 31 Dec 20:00 notification is exact.
     */
    private fun requestExactAlarmAccessIfNeeded() {
        // SCHEDULE_EXACT_ALARM only exists from Android 12 (API 31); asking for it
        // on Android 10/11 would throw and there is nothing to grant there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        if (alarmManager.canScheduleExactAlarms()) return

        val prefs = getSharedPreferences("cypher_wrapped", MODE_PRIVATE)
        if (prefs.getBoolean("exact_alarm_requested", false)) return
        prefs.edit().putBoolean("exact_alarm_requested", true).apply()

        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, audioPermission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(audioPermission)
        }
        // Notification permission only became a runtime grant in Android 13.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
