package com.pranshulgg.recordmaster.ui.expressive

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Adapted from material-app/core/designsystem/Shape.kt.
// Material 3 Expressive prefers larger, rounder containers than the baseline
// 4/8/12/16/28 scale, so medium and large grow to 16 and 24.
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
