package com.cypher.music.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cypher.music.network.YouTubeAccountManager

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginDialog(
    accountManager: YouTubeAccountManager,
    accentColor: Color,
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F&hl=es") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isSessionDetected by remember { mutableStateOf(false) }
    var currentDisplayUrl by remember { mutableStateOf("") }

    fun checkAndSaveSession(force: Boolean = false): Boolean {
        val allCookies = YouTubeAccountManager.collectAllYouTubeCookies()
        val sapisid = YouTubeAccountManager.extractCookieValue(allCookies, "SAPISID")
            ?: YouTubeAccountManager.extractCookieValue(allCookies, "__Secure-3PAPISID")
            ?: YouTubeAccountManager.extractCookieValue(allCookies, "__Secure-1PAPISID")

        val hasYouTubeAuth = !sapisid.isNullOrBlank() && (
            allCookies.contains("LOGIN_INFO") || 
            currentDisplayUrl.contains("music.youtube.com") ||
            force
        )

        if (hasYouTubeAuth) {
            isSessionDetected = true
            accountManager.saveCookies(allCookies)
            return true
        }
        return false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
            color = Color(0xFF121214)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18181B))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Iniciar sesión con Google",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isSessionDetected) "✓ Sesión lista para sincronizar" else "YouTube Music & InnerTube Sync",
                                color = if (isSessionDetected) accentColor else Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontWeight = if (isSessionDetected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = accentColor,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        IconButton(
                            onClick = {
                                webViewInstance?.reload()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Recargar",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // WebView Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF000000))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                YouTubeAccountManager.injectConsentCookies()

                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                        currentDisplayUrl = url ?: ""
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        val checkUrl = url ?: ""
                                        currentDisplayUrl = checkUrl

                                        // Only auto-trigger completion when reaching YouTube Music domain
                                        if (checkUrl.contains("music.youtube.com")) {
                                            val ok = checkAndSaveSession(force = false)
                                            if (ok) {
                                                // Give a moment for passive cookies to settle, then notify
                                                view?.postDelayed({
                                                    if (checkAndSaveSession(force = true)) {
                                                        onLoginSuccess()
                                                    }
                                                }, 1800)
                                            }
                                        }
                                    }

                                    // Keep the login surface restricted to Google/YouTube hosts: a
                                    // redirect to any other site must not be loaded inside the session.
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val host = request?.url?.host?.lowercase() ?: return false
                                        val allowed = host == "google.com" || host.endsWith(".google.com") ||
                                            host == "youtube.com" || host.endsWith(".youtube.com") ||
                                            host.endsWith(".gstatic.com") ||
                                            host.endsWith(".googleusercontent.com")
                                        return !allowed
                                    }
                                }

                                loadUrl(currentUrl)
                                webViewInstance = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18181B))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSessionDetected) "Sesión identificada" else "Accede a tu cuenta",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = {
                            val web = webViewInstance
                            val cur = currentDisplayUrl
                            if (!cur.contains("music.youtube.com") && web != null) {
                                // If user is finished on Google but not redirected yet, navigate to music.youtube.com
                                web.loadUrl("https://music.youtube.com/")
                            } else {
                                if (checkAndSaveSession(force = true)) {
                                    onLoginSuccess()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isSessionDetected || currentDisplayUrl.contains("music.youtube.com")) "Completar sincronización" else "Detectar sesión",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
