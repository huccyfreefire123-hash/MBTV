package com.ondatv.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Colours measured from the reference screenshots. */
object Onda {
    // Screen behind lists: slate blue at the top fading to near black.
    val bgTop = Color(0xFF5B7284)
    val bgMid = Color(0xFF27324A)
    val bgBottom = Color(0xFF0B0C14)

    // Screen behind the large banner screens.
    val heroBg = Color(0xFF16171F)

    // Frosted controls.
    val glass = Color(0x33FFFFFF)
    val glassBorder = Color(0x4DFFFFFF)
    val bar = Color(0xEB2A2C34)
    val barBorder = Color(0x26FFFFFF)
    val barSelected = Color(0x29FFFFFF)

    val card = Color(0xFF2A2B33)
    val chip = Color(0xCC1E1F25)
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
    background = Onda.heroBg,
    surface = Color(0xFF22232B),
    surfaceVariant = Onda.card,
    surfaceContainer = Color(0xFF22232B),
    surfaceContainerHigh = Color(0xFF2A2B33),
    onSurface = Color.White,
    onSurfaceVariant = Onda.textFaint,
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
