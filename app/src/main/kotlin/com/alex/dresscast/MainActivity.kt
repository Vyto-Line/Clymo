package com.alex.dresscast

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alex.dresscast.ui.BottomBar
import com.alex.dresscast.ui.SettingsScreen
import com.alex.dresscast.ui.SetupScreen
import com.alex.dresscast.ui.TodayScreen
import com.alex.dresscast.ui.WeekScreen
import com.alex.dresscast.ui.palette

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = AppState(applicationContext)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Scheduler.scheduleAll(this)
        setContent { App(state, this) }
    }

    override fun onResume() {
        super.onResume()
        L.use(state.prefs)
        state.refresh()
    }
}

@Composable
fun App(s: AppState, activity: ComponentActivity) {
    val p = s.prefs
    val dark = when (p.theme) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }
    val mood = s.forecast?.days?.firstOrNull()?.mood ?: Mood.Mild
    val pal = palette(mood, dark)

    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
        else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    val scheme = if (dark) darkColorScheme(primary = pal.accent, onPrimary = pal.onAccent, surface = pal.card)
    else lightColorScheme(primary = pal.accent, onPrimary = pal.onAccent, surface = pal.card)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = scheme) {
      androidx.compose.runtime.CompositionLocalProvider(
          androidx.compose.material3.LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = com.alex.dresscast.ui.Fonts.body),
      ) {
       androidx.compose.runtime.key(s.langVersion) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pal.bg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (!p.onboarded) {
                SetupScreen(s, pal)
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> TodayScreen(s, pal)
                            1 -> WeekScreen(s, pal)
                            else -> SettingsScreen(s, pal)
                        }
                    }
                    BottomBar(tab, pal) { tab = it }
                }
            }
        }
       }
      }
    }
}
