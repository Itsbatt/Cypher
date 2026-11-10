package com.cypher.music

import android.Manifest
import android.app.SearchManager
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
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
import com.cypher.music.data.ShortcutAction
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
            // The year travels as an Intent extra that anyone can craft, so it is
            // clamped to the range the recap can actually display.
            val requestedYear = intent.getIntExtra(WrappedScheduler.EXTRA_YEAR, WrappedScheduler.YEAR)
            val year = if (requestedYear in VALID_RECAP_YEARS) requestedYear else WrappedScheduler.YEAR
            if (year != requestedYear) {
                Log.w(TAG, "Ignoring out-of-range wrapped year: $requestedYear")
            }
            viewModel.openWrapped(year)
            return
        }

        // Long-press shortcuts on the app icon. They are explicit Intents to this
        // activity; the action is consumed once so a rotation or a re-delivery does
        // not start playback again.
        ShortcutAction.fromIntentAction(intent.action)?.let { shortcut ->
            intent.action = null
            viewModel.handleShortcut(shortcut)
            return
        }

        val action = intent.action ?: return

        // Assistant, Bixby and the system search bar send the query here. The intent
        // was declared in the manifest but never handled, so voice search did nothing.
        if (action == Intent.ACTION_SEARCH || action == "android.media.action.MEDIA_PLAY_FROM_SEARCH") {
            val query = (intent.getStringExtra(SearchManager.QUERY)
                ?: intent.getStringExtra("query"))
                ?.trim()
            if (query.isNullOrEmpty() || query.length > MAX_SEARCH_QUERY_LENGTH) {
                if (!query.isNullOrEmpty()) {
                    Log.w(TAG, "Ignoring oversized search query (${query.length} chars)")
                }
                return
            }
            viewModel.selectTab(CypherTab.SEARCH)
            viewModel.onSearchQueryChange(query)
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

        /** Recap years the app is willing to open from an external Intent. */
        private val VALID_RECAP_YEARS = 2000..2100

        /** External searches (assistant, Bixby) are capped like the in-app field. */
        private const val MAX_SEARCH_QUERY_LENGTH = 200
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
