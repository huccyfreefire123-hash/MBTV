package com.ondatv.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The MBTV AMOLED palette: pure black with neutral greys (the same surfaces as the MBTV screen,
 * see ytxtube/Theme.kt), so the whole app looks like one. The gradients stay, now grey to black.
 */
object Onda {
    // Screen behind lists: grey at the top fading to pure black.
    val bgTop = Color(0xFF262626)
    val bgMid = Color(0xFF0F0F0F)
    val bgBottom = Color.Black

    // Screen behind the large banner screens.
    val heroBg = Color.Black

    // Frosted controls.
    val glass = Color(0x33FFFFFF)
    val glassBorder = Color(0x4DFFFFFF)
    val bar = Color(0xEB141414)
    val barBorder = Color(0x26FFFFFF)
    val barSelected = Color(0x29FFFFFF)

    val card = Color(0xFF141414)
    val chip = Color(0xCC0C0C0C)
    val divider = Color(0x1FFFFFFF)

    val text = Color.White
    val textDim = Color(0xD9FFFFFF)
    val textFaint = Color(0x80FFFFFF)
    val gold = Color(0xFFF5C518)

    val listBackground = Brush.verticalGradient(
        0f to bgTop,
        0.45f to bgMid,
        1f to bgBottom,
    )
}

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Onda.gold,
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF141414),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1E1E1E),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = Onda.textFaint,
    outlineVariant = Color(0xFF1F1F1F),
)

// The reference has no extra letter spacing; Material adds some by default.
private val typography = Typography().let { t ->
    t.copy(
        bodyLarge = t.bodyLarge.copy(letterSpacing = 0.sp),
        bodyMedium = t.bodyMedium.copy(letterSpacing = 0.sp),
        bodySmall = t.bodySmall.copy(letterSpacing = 0.sp),
        labelLarge = t.labelLarge.copy(letterSpacing = 0.sp),
        labelMedium = t.labelMedium.copy(letterSpacing = 0.sp),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.sp),
        titleMedium = t.titleMedium.copy(letterSpacing = 0.sp),
    )
}

@Composable
fun OndaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
