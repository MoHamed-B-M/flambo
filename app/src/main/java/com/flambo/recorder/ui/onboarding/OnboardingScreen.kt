package com.flambo.recorder.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flambo.recorder.ui.theme.ShapeExtraExtraLarge
import com.flambo.recorder.ui.theme.ShapeFull
import kotlinx.coroutines.launch

private val pages = listOf(
    Triple(
        Icons.Filled.Mic,
        "Tap record. That's it.",
        "One tap starts capturing. Pause and resume freely — Flambo keeps a single clean timeline, even with the screen off."
    ) to @Composable { MaterialTheme.colorScheme.primaryContainer },
    Triple(
        Icons.Filled.RecordVoiceOver,
        "Words, kept.",
        "Turn any recording into text on your device with offline transcription. Copy it, share it, search it later."
    ) to @Composable { MaterialTheme.colorScheme.secondaryContainer },
    Triple(
        Icons.Filled.Shield,
        "Private by design.",
        "Everything lives on your phone — recordings, transcripts, favorites. No account, no cloud, no surprises."
    ) to @Composable { MaterialTheme.colorScheme.tertiaryContainer },
)

private val bouncy = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    micGranted: Boolean = true,
    notifGranted: Boolean = true,
    showNotificationsRow: Boolean = true,
    onGrantMic: () -> Unit = {},
    onGrantNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Three story pages plus a final permissions page.
    val totalPages = pages.size + 1
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage
    val isLast = page == totalPages - 1

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 32.dp)
        ) {
            // Skip stays visible until the last page
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isLast) {
                    TextButton(onClick = onFinish) { Text("Skip") }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) { index ->
                if (index >= pages.size) {
                    PermissionPageContent(
                        micGranted = micGranted,
                        notifGranted = notifGranted,
                        showNotificationsRow = showNotificationsRow,
                        onGrantMic = onGrantMic,
                        onGrantNotifications = onGrantNotifications
                    )
                } else {
                    val (content, container) = pages[index]
                    val (icon, title, body) = content
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                    // Hero morphs per page — bouncy scale-in on each swipe
                    AnimatedContent(
                        targetState = index,
                        transitionSpec = {
                            (slideInHorizontally(
                                initialOffsetX = { if (targetState > initialState) it / 3 else -it / 3 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + scaleIn(initialScale = 0.7f, animationSpec = bouncy) + fadeIn()) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { if (targetState > initialState) -it / 3 else it / 3 }
                                ) + fadeOut())
                        },
                        label = "onboarding-hero"
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(168.dp)
                                .clip(ShapeExtraExtraLarge)
                                .background(container())
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    }
                }
            }

            // Bouncy dot pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
            ) {
                (0 until totalPages).forEach { i ->
                    val width by animateDpAsState(
                        targetValue = if (i == page) 32.dp else 8.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "dot-$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 8.dp)
                            .clip(ShapeFull)
                            .background(
                                if (i == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                    )
                }
            }

            // Back / Next with morphing expressive shapes
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (page > 0) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                        shapes = ButtonDefaults.shapes()
                    ) { Text("Back") }
                }
                Spacer(Modifier.weight(1f))
                if (isLast) {
                    Button(
                        onClick = onFinish,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight)
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start recording", style = ButtonDefaults.textStyleFor(ButtonDefaults.LargeContainerHeight))
                    }
                } else {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(page + 1) } },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
                    ) {
                        Text("Next", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPageContent(
    micGranted: Boolean,
    notifGranted: Boolean,
    showNotificationsRow: Boolean,
    onGrantMic: () -> Unit,
    onGrantNotifications: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(168.dp)
                .clip(ShapeExtraExtraLarge)
                .background(MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Let Flambo hear",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Microphone access is required for every recording — including system sound, which Android only allows with it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(20.dp))
        PermissionRow(
            icon = Icons.Filled.Mic,
            title = "Microphone",
            body = "Record your voice and the room",
            granted = micGranted,
            onGrant = onGrantMic
        )
        if (showNotificationsRow) {
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                body = "Recording timer and update alerts",
                granted = notifGranted,
                onGrant = onGrantNotifications
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Surface(
        shape = ShapeFull,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                Text(
                    "Allowed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = onGrant,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
                ) {
                    Text("Allow", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }
        }
    }
}
