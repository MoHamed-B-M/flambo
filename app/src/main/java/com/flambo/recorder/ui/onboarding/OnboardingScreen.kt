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
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage
    val isLast = page == pages.lastIndex

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

            // Bouncy dot pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
            ) {
                pages.indices.forEach { i ->
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
