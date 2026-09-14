package com.flambo.recorder.ui.intro

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flambo.recorder.R
import com.flambo.recorder.data.SoundPlayer
import kotlinx.coroutines.delay

@Composable
fun IntroScreen(
    onFinished: () -> Unit,
    durationMs: Int = 3000
) {
    val context = LocalContext.current
    var started by remember { mutableStateOf(false) }

    // Zoom: 0.6 -> 1.0 over 3s, expressive overshoot style
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.6f,
        animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        label = "intro-scale"
    )
    // Fade: 0 -> 1
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        label = "intro-alpha"
    )
    // Slight fade-in for title after icon
    val titleAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 1800, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "intro-title-alpha"
    )

    LaunchedEffect(Unit) {
        // Start animation + sound together
        started = true
        SoundPlayer.playIntro(context)
        delay(durationMs.toLong())
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Icon with zoom + fade
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
                    .alpha(alpha)
            ) {
                // Use the vector from intro.svg (now drawable/intro.xml)
                Icon(
                    painter = painterResource(id = R.drawable.intro),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(120.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Flambo",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(titleAlpha)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "A calm yet expressive voice recorder",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(titleAlpha)
            )
        }
    }
}
