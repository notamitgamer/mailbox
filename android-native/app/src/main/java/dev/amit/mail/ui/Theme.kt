package dev.amit.mail.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// The graphite and teal palette (#121416, #1b1e21, #4fd1b5, #e6e8ea) expressed as Material 3 colour roles.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1B5), onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142), onPrimaryContainer = Color(0xFF7FF0D3),
    secondary = Color(0xFFB1CCC3), onSecondary = Color(0xFF1C352E),
    secondaryContainer = Color(0xFF2C443D), onSecondaryContainer = Color(0xFFCDE8DF),
    tertiary = Color(0xFFA9CBE1), onTertiary = Color(0xFF0E3446),
    tertiaryContainer = Color(0xFF2A4A5E), onTertiaryContainer = Color(0xFFC5E7FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121416), onBackground = Color(0xFFE6E8EA),
    surface = Color(0xFF121416), onSurface = Color(0xFFE6E8EA),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF89938F), outlineVariant = Color(0xFF3F4945),
    inverseSurface = Color(0xFFE6E8EA), inverseOnSurface = Color(0xFF2E3133), inversePrimary = Color(0xFF006B58),
    surfaceTint = Color(0xFF4FD1B5),
    surfaceDim = Color(0xFF121416), surfaceBright = Color(0xFF38393C),
    surfaceContainerLowest = Color(0xFF0D0F11), surfaceContainerLow = Color(0xFF181B1D),
    surfaceContainer = Color(0xFF1B1E21), surfaceContainerHigh = Color(0xFF25282B), surfaceContainerHighest = Color(0xFF303336),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B58), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7FF0D3), onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4A635B), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8DF), onSecondaryContainer = Color(0xFF06201A),
    tertiary = Color(0xFF3F6279), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC5E7FF), onTertiaryContainer = Color(0xFF001E2E),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF8), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FBF8), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E0), onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975), outlineVariant = Color(0xFFBFC9C4),
    inverseSurface = Color(0xFF2B322F), inverseOnSurface = Color(0xFFECF2EF), inversePrimary = Color(0xFF4FD1B5),
    surfaceTint = Color(0xFF006B58),
    surfaceDim = Color(0xFFD5DBD8), surfaceBright = Color(0xFFF5FBF8),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEC), surfaceContainerHigh = Color(0xFFE3EAE6), surfaceContainerHighest = Color(0xFFDEE4E1),
)

@Composable
fun isDarkTheme(mode: String): Boolean = when (mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun MailTheme(mode: String, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
