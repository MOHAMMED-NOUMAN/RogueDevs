package com.itantra.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.itantra.app.navigation.ItantraNavGraph
import com.itantra.app.ui.theme.ItantraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appStart: AppStartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { appStart.startRoute.value == null }
        showOverLockScreenFor(intent)
        // Every screen has a light background, so the bar icons must stay dark even when the
        // phone is in dark mode (otherwise they turn white and vanish, e.g. on Samsung).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            ItantraTheme {
                val startRoute by appStart.startRoute.collectAsState()
                startRoute?.let { ItantraNavGraph(startDestination = it) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showOverLockScreenFor(intent)
    }

    /** An incoming SOS opens the app on top of the lock screen and turns the screen on. */
    private fun showOverLockScreenFor(intent: Intent?) {
        val sos = intent?.getBooleanExtra(EXTRA_SOS, false) == true
        setShowWhenLocked(sos)
        setTurnScreenOn(sos)
    }

    companion object {
        /** Set on the SOS notification's intent. */
        const val EXTRA_SOS = "com.itantra.app.extra.SOS"
    }
}
