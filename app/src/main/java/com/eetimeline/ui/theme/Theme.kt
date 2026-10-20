package com.eetimeline.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
        content = content,
    )
}
