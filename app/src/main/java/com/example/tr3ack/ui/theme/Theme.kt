package com.example.tr3ack.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    primaryContainer = Green80,
    secondaryContainer = Blue80,
)

// Unified text/icon color: every light-on-dark glyph uses this off-white.
private val Glyph = Color(0xFFCDD6F4)

@Composable
fun Tr3ackTheme(
    content: @Composable () -> Unit
) {
    val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        DarkColorScheme
    }

    // Force all body text, secondary text and icons to the unified glyph color
    // while keeping the (dynamic) surfaces/accent colors untouched.
    val colorScheme = base.copy(
        onBackground = Glyph,
        onSurface = Glyph,
        onSurfaceVariant = Glyph,
        onPrimaryContainer = Glyph,
        onSecondaryContainer = Glyph,
        onTertiaryContainer = Glyph,
        outline = Glyph,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
