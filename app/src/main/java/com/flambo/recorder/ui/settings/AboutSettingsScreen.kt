package com.flambo.recorder.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.compose.ui.layout.ContentScale
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.flambo.recorder.R
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.ui.settings.components.SectionHeader
import com.flambo.recorder.ui.settings.components.SegmentedPreferenceGroup
import com.flambo.recorder.ui.theme.ShapeFull
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    prefs: PreferencesManager,
    scope: CoroutineScope,
    onBack: () -> Unit,
    onRerunOnboarding: () -> Unit
) {
    val tipsEnabled by prefs.tipsEnabledFlow.collectAsState(initial = true)
    val uriHandler = LocalUriHandler.current


    val gestureEnabled by prefs.gestureEnabledFlow.collectAsState(initial = true)
    val scale = remember { Animatable(1f) }
    val corner = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val velocityTracker = remember { VelocityTracker() }
    PredictiveBackHandler(enabled = gestureEnabled) { progress ->
        try {
            progress.collect { event ->
                val p = event.progress
                scale.snapTo((1f - p * 0.05f).coerceIn(0.95f, 1f))
                corner.snapTo(p * 24f)
                alpha.snapTo((1f - p * 0.15f).coerceIn(0.85f, 1f))
            }
            onBack()
        } catch (e: CancellationException) {
            gestureScope.launch {
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                alpha.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX.value
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                shape = RoundedCornerShape(corner.value.dp)
                clip = corner.value > 0f
            }
            .clip(RoundedCornerShape(corner.value.dp))
            .pointerInput(gestureEnabled) {
                if (!gestureEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onDragEnd = {
                        val offset = offsetX.value
                        val velocity = runCatching { velocityTracker.calculateVelocity().x }.getOrDefault(0f)
                        val dismissDistance = with(density) { 100.dp.toPx() }
                        val velocityThreshold = with(density) { 400.dp.toPx() }
                        val shouldDismiss = offset > dismissDistance || velocity > velocityThreshold
                        if (shouldDismiss) {
                            gestureScope.launch { offsetX.animateTo(with(density) { 1080.dp.toPx() }, spring(stiffness = Spring.StiffnessMedium)) }
                            gestureScope.launch { kotlinx.coroutines.delay(80); if (offsetX.value >= with(density) { 1080.dp.toPx() } * 0.5f) onBack() }
                        } else {
                            gestureScope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                alpha.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        }
                    },
                    onDragCancel = {
                        gestureScope.launch {
                            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            alpha.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val newOffset = max(0f, offsetX.value + dragAmount)
                        gestureScope.launch {
                            offsetX.snapTo(newOffset)
                            val progress = (newOffset / with(density) { 1080.dp.toPx() }).coerceIn(0f, 1f)
                            scale.snapTo((1f - progress * 0.05f).coerceIn(0.95f, 1f))
                            corner.snapTo(progress * 24f)
                            alpha.snapTo((1f - progress * 0.15f).coerceIn(0.85f, 1f))
                        }
                    }
                )
            }
    ) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
                .padding(horizontal = 16.dp)
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Guidance")
                SegmentedPreferenceGroup {
                    item {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = { Text("Replay onboarding") },
                                supportingContent = { Text("Take the quick tour again") },
                                leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = {
                                    TextButton(onClick = onRerunOnboarding, shapes = ButtonDefaults.shapes()) { Text("Replay") }
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            )
                        }
                    }
                    item {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = { Text("Show tips") },
                                supportingContent = { Text("Short how-tos on the home screen") },
                                leadingContent = { Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = {
                                    Switch(
                                        checked = tipsEnabled,
                                        onCheckedChange = {
                                            scope.launch {
                                                prefs.setTipsEnabled(it)
                                                if (it) prefs.setTipIndex(0)
                                            }
                                        },
                                        thumbContent = if (tipsEnabled) {
                                            { Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                                        } else null
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            )
                        }
                    }
                }
            }

            androidx.compose.material3.Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = ShapeFull, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Flambo", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            val context = LocalContext.current
                            val versionLabel = remember {
                                try {
                                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                    val vName = pInfo.versionName ?: "1.4.0"
                                    val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode.toLong()
                                    "v$vName ($vCode)"
                                } catch (_: Exception) { "v1.4.0" }
                            }
                            AssistChip(onClick = {}, label = { Text(versionLabel) })
                        }
                        Text("A calm, expressive voice recorder. Transcripts and recordings stay on your device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            androidx.compose.material3.Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.github_avatar),
                            contentDescription = "MoHamed-B-M profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Hamma", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("@MoHamed-B-M", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Indie Android dev • voice nerd", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { uriHandler.openUri("https://github.com/MoHamed-B-M") },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("GitHub")
                        }
                        OutlinedButton(
                            onClick = { uriHandler.openUri("https://github.com/MoHamed-B-M/flambo/issues/new") },
                            shape = ShapeFull,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Report issue")
                        }
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
    }
}

