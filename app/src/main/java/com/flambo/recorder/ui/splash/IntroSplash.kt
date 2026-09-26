package com.flambo.recorder.ui.splash

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/** Full intro length: text reveals at 2.3s + 0.8s, plus a small buffer. */
private const val INTRO_DURATION_MS = 3300L

/**
 * Launch intro: plays the brand SVG (CSS keyframe animation, driven by a
 * WebView since SVG animation isn't playable via Compose image loaders).
 * Auto-dismisses when the animation ends; tap or back skips. The main
 * content loads underneath so nothing waits on the intro.
 */
@Composable
fun IntroSplash(onDone: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onDone)
    LaunchedEffect(Unit) {
        delay(INTRO_DURATION_MS)
        onDone()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone
            )
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(0xFF090A0F.toInt())
                    settings.apply {
                        javaScriptEnabled = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    // HTML shell (not the raw .svg): a bare SVG document is
                    // unreliable in WebView and paints only its background.
                    loadUrl("file:///android_asset/flambo_intro.html")
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize()
        )
    }
}
