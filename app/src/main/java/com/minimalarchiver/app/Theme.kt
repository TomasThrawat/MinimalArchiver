package com.minimalarchiver.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// True AMOLED pure-black scheme: every surface/background role forced to #000000
// so Material3's tonal-elevation overlay can't lighten dialogs, menus, or cards.
val PureBlackColorScheme = darkColorScheme(
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color.Black,
    onSurfaceVariant = Color.White,
    surfaceContainer = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
    surfaceTint = Color.Black,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    scrim = Color.Black,
)
