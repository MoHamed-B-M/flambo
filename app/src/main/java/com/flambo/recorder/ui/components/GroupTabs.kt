package com.flambo.recorder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabPosition
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A named group of recordings with a recording count and an optional folder color. */
data class GroupFolder(
    val name: String,
    val count: Int,
    val colorArgb: Int?
)

/** Small fixed palette for folder colors. */
val GROUP_PALETTE = listOf(
    0xFFE53935.toInt(),
    0xFFFB8C00.toInt(),
    0xFFFFB300.toInt(),
    0xFF43A047.toInt(),
    0xFF00897B.toInt(),
    0xFF1E88E5.toInt(),
    0xFF8E24AA.toInt(),
    0xFFD81B60.toInt()
)

@Composable
fun LibraryTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val titles = listOf("Home", "Groups")
    SecondaryTabRow(
        selectedTabIndex = selectedIndex,
        indicator = { FancyAnimatedIndicator(selectedIndex) },
        modifier = modifier
    ) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                text = { Text(title) }
            )
        }
    }
}

/**
 * Animated rounded-rectangle outline that stretches between tabs with
 * direction-aware spring physics (adapted from the M3 expressive catalog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabIndicatorScope.FancyAnimatedIndicator(index: Int) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
    var startAnimatable by remember { mutableStateOf<Animatable<Dp, AnimationVector1D>?>(null) }
    var endAnimatable by remember { mutableStateOf<Animatable<Dp, AnimationVector1D>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val indicatorColor: Color by animateColorAsState(colors[index % colors.size], label = "tabIndicator")

    Box(
        Modifier.tabIndicatorLayout { measurable: Measurable, constraints: Constraints, tabPositions: List<TabPosition> ->
            val newStart = tabPositions[index].left
            val newEnd = tabPositions[index].right
            val startAnim =
                startAnimatable
                    ?: Animatable(newStart, Dp.VectorConverter).also { startAnimatable = it }
            val endAnim =
                endAnimatable
                    ?: Animatable(newEnd, Dp.VectorConverter).also { endAnimatable = it }

            if (endAnim.targetValue != newEnd) {
                coroutineScope.launch {
                    endAnim.animateTo(
                        newEnd,
                        animationSpec =
                        if (endAnim.targetValue < newEnd) {
                            spring(dampingRatio = 1f, stiffness = 1000f)
                        } else {
                            spring(dampingRatio = 1f, stiffness = 50f)
                        }
                    )
                }
            }

            if (startAnim.targetValue != newStart) {
                coroutineScope.launch {
                    startAnim.animateTo(
                        newStart,
                        animationSpec =
                        // The leading edge moves faster in the travel direction,
                        // so the outline stretches toward the target tab.
                        if (startAnim.targetValue < newStart) {
                            spring(dampingRatio = 1f, stiffness = 50f)
                        } else {
                            spring(dampingRatio = 1f, stiffness = 1000f)
                        }
                    )
                }
            }

            val indicatorEnd = endAnim.value.roundToPx()
            val indicatorStart = startAnim.value.roundToPx()
            val placeable = measurable.measure(
                constraints.copy(
                    maxWidth = indicatorEnd - indicatorStart,
                    minWidth = indicatorEnd - indicatorStart
                )
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(indicatorStart, 0)
            }
        }
            .padding(5.dp)
            .fillMaxSize()
            .drawWithContent {
                drawRoundRect(
                    color = indicatorColor,
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
    )
}

@Composable
fun GroupColorRow(
    selectedArgb: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        GROUP_PALETTE.forEach { argb ->
            val selected = selectedArgb == argb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        2.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
                    .clickable { onSelect(argb) }
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GroupFolderCard(
    group: GroupFolder,
    onOpen: () -> Unit,
    onRecolor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folderColor = group.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    Card(
        onClick = onOpen,
        shape = ShapeLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = folderColor,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.count} recordings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRecolor) {
                Icon(Icons.Filled.Palette, contentDescription = "Folder color")
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
