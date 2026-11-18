package com.pranshulgg.recordmaster.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Seed colours are stored as hex strings in PreferencesHelper: the defaults use
 * "0xff0000FF" while the colour picker saves "0xFF0000FF". Both spellings reach
 * this parser, so normalise the prefix instead of assuming one of them.
 */
fun parseSeedColor(seed: String): Color = runCatching {
    Color(seed.trim().removePrefix("0x").removePrefix("0X").toLong(16).toInt())
}.getOrDefault(Color(0xFF0000FF))
