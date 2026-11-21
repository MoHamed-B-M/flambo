package com.pranshulgg.recordmaster.core.domain

/**
 * Built-in font choices for app typography.
 *
 * [id] is persisted, so display names and Google Fonts mappings can change
 * without invalidating a saved choice.
 */
enum class AppFont(
    val id: String,
    val displayName: String,
    val googleFontName: String? = null,
) {
    DEFAULT("default", "Default (System/Roboto)"),
    ROBOTO_SERIF("roboto_serif", "Roboto Serif", "Roboto Serif"),
    ROBOTO_MONO("roboto_mono", "Roboto Mono", "Roboto Mono"),
    NOTO_SANS("noto_sans", "Noto Sans", "Noto Sans"),
    // "Sans Flex" is surfaced with a shorter label; Roboto Flex is the
    // downloadable family that provides the variable sans experience.
    SANS_FLEX("sans_flex", "Sans Flex", "Roboto Flex"),
    // Material 3 Expressive's rounded, variable sans is represented by
    // Google Sans Flex, the closest family available through Google Fonts.
    MATERIAL_3_EXPRESSIVE_ROUNDED(
        "material_3_expressive_rounded",
        "Material 3 Expressive Rounded",
        "Google Sans Flex",
    );

    companion object {
        val available: List<AppFont> = entries.toList()

        fun fromId(id: String?): AppFont = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
