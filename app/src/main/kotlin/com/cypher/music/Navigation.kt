package com.cypher.music

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.cypher.music.data.LegalNoticeStore
import com.cypher.music.data.MotionPreferences
import com.cypher.music.ui.components.LegalNoticeDialog
import com.cypher.music.ui.main.MainScreen
import com.cypher.music.ui.theme.LocalReducedMotion

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val motionPreferences = remember { MotionPreferences(context) }
    val legalNoticeStore = remember { LegalNoticeStore(context) }
    val reducedMotion by motionPreferences.reducedMotion.collectAsState()
    var showLegalNotice by remember { mutableStateOf(!legalNoticeStore.hasAccepted()) }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MainScreen(modifier = Modifier.fillMaxSize())
    }

    if (showLegalNotice) {
        LegalNoticeDialog(
            onDismiss = {
                legalNoticeStore.accept()
                showLegalNotice = false
            }
        )
    }
}
