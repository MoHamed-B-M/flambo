package com.flambo.recorder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// MD3 shape tokens — expressive values.
// Buttons → full, cards → medium, dialogs → extraLarge, record FAB → large / extraLarge morph.
val FlamboShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),  // chips, snackbars
    small = RoundedCornerShape(8.dp),       // text fields, menus
    medium = RoundedCornerShape(12.dp),     // cards
    large = RoundedCornerShape(16.dp),      // FAB, nav drawer
    extraLarge = RoundedCornerShape(28.dp)  // dialogs, bottom sheets
)

// Extra expressive tokens not in Shapes directly — use in specific components
val ShapeLargeIncreased = RoundedCornerShape(20.dp)
val ShapeExtraLargeIncreased = RoundedCornerShape(32.dp)
val ShapeExtraExtraLarge = RoundedCornerShape(48.dp)
val ShapeFull = RoundedCornerShape(999.dp)
