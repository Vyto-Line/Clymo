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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alex.dresscast.ui.BottomBar
import com.alex.dresscast.ui.DetailsScreen
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
        state.prefs.reload()
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val base = palette(mood, dark)
    val pal = com.alex.dresscast.ui.animated(
        if (p.dynamic && android.os.Build.VERSION.SDK_INT >= 31) com.alex.dresscast.ui.dynamicPal(base, ctx, dark) else base
    )

    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
        else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    val scheme = if (dark) darkColorScheme(primary = pal.accent, onPrimary = pal.onAccent, surface = pal.card)
    else lightColorScheme(primary = pal.accent, onPrimary = pal.onAccent, surface = pal.card)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = showDetails) { showDetails = false }
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
                // The details screen rises over the tabs; tabs slide sideways when switched.
                AnimatedContent(
                    targetState = showDetails,
                    transitionSpec = {
                        if (targetState) (slideInVertically(tween(380)) { it / 6 } + fadeIn(tween(300)) + scaleIn(tween(380), initialScale = 0.96f))
                            .togetherWith(fadeOut(tween(200)))
                        else fadeIn(tween(250)).togetherWith(slideOutVertically(tween(300)) { it / 6 } + fadeOut(tween(250)))
                    },
                    label = "details",
                ) { details ->
                    if (details) {
                        DetailsScreen(s, pal) { showDetails = false }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = tab,
                                modifier = Modifier.weight(1f),
                                transitionSpec = {
                                    val dir = if (targetState > initialState) 1 else -1
                                    (slideInHorizontally(tween(320)) { dir * it / 5 } + fadeIn(tween(320)))
                                        .togetherWith(slideOutHorizontally(tween(260)) { -dir * it / 5 } + fadeOut(tween(200)))
                                },
                                label = "tabs",
                            ) { current ->
                                when (current) {
                                    0 -> TodayScreen(s, pal, onOpenDetails = { showDetails = true }, onOpenWeek = { tab = 1 })
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
    }
}
