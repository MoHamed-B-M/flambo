package com.pranshulgg.recordmaster.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font as ComposeFont
import androidx.compose.ui.text.googlefonts.Font as GoogleFontFont
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.core.domain.AppFont
import java.io.File

private val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val CatalogWeights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

/** Resolves a persisted font choice to the family used by Compose. */
fun fontFamilyFor(font: AppFont, customFontFile: File? = null): FontFamily {
    if (font == AppFont.DEFAULT) return FontFamily.Default

    if (font == AppFont.CUSTOM) {
        val file = customFontFile?.takeIf { it.exists() } ?: return FontFamily.Default
        return FontFamily(
            *CatalogWeights.map { weight ->
                ComposeFont(file, weight, FontStyle.Normal)
            }.toTypedArray()
        )
    }

    val googleFont = GoogleFont(requireNotNull(font.googleFontName) {
        "${font.name} does not have a Google Fonts mapping"
    })
    return FontFamily(
        *CatalogWeights.map { weight ->
            GoogleFontFont(googleFont, GoogleFontProvider, weight, FontStyle.Normal)
        }.toTypedArray()
    )
}
