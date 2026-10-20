package com.eetimeline.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eetimeline.R

private val Fraunces = FontFamily(
    Font(
        R.font.fraunces,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.fraunces,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.fraunces,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.fraunces,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

private val EETimelineTypography = Typography().let { defaults ->
    defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = Fraunces, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.6).sp),
        displayMedium = defaults.displayMedium.copy(fontFamily = Fraunces, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.45).sp),
        displaySmall = defaults.displaySmall.copy(fontFamily = Fraunces, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = Fraunces, fontSize = 29.sp, lineHeight = 35.sp, letterSpacing = (-0.25).sp),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = Fraunces, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = Fraunces, fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.15).sp),
        titleLarge = defaults.titleLarge.copy(fontFamily = Fraunces, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.1).sp),
        titleMedium = defaults.titleMedium.copy(fontFamily = Fraunces, fontSize = 18.sp, lineHeight = 24.sp),
        titleSmall = defaults.titleSmall.copy(fontFamily = Fraunces, fontSize = 16.sp, lineHeight = 22.sp),
    )
}

val Night = Color(0xFF15171D)
val Surface = Color(0xFF20232B)
val SurfaceRaised = Color(0xFF292D37)
val Cream = Color(0xFFF5EBDD)
val MutedCream = Color(0xFFB9B2A8)
val Apricot = Color(0xFFF6B27F)
val Mint = Color(0xFFA5D2B9)
val BlueGrey = Color(0xFF9BB7CC)
val Plum = Color(0xFFD0A5C8)
val Gold = Color(0xFFE7C578)

private val EETimelineColors = darkColorScheme(
    primary = Apricot,
    onPrimary = Color(0xFF2D1B12),
    secondary = Mint,
    onSecondary = Color(0xFF12241C),
    tertiary = BlueGrey,
    background = Night,
    onBackground = Cream,
    surface = Surface,
    onSurface = Cream,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = MutedCream,
    outline = Color(0xFF535763),
    error = Color(0xFFFFB4AB),
)

val UnitColors = listOf(
    Color(0xFF72A99C),
    Color(0xFF99B6D2),
    Color(0xFFCC9D70),
    Color(0xFFBD8F9A),
    Color(0xFFA9B87D),
    Color(0xFFD39C6E),
    Color(0xFF948BC0),
    Color(0xFF72AFC1),
    Color(0xFFDA9D85),
)

fun unitColor(number: Int): Color = UnitColors[(number - 1).mod(UnitColors.size)]

@Composable
fun EETimelineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EETimelineColors,
        typography = EETimelineTypography,
        content = content,
    )
}
