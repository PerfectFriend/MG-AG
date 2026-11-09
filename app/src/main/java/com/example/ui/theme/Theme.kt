package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WarmGold,
    secondary = AmberWood,
    tertiary = Mahogany,
    background = ObsidianBackground,
    surface = WarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = OnWarmSurface,
    onSurface = OnWarmSurface,
    errorContainer = Color(0xFF5E1E1E),
    onErrorContainer = Color(0xFFFFDADA),
    primaryContainer = WarmSurfaceVariant,
    onPrimaryContainer = OnWarmSurface
)

private val LightColorScheme = lightColorScheme(
    primary = Mahogany,
    secondary = AmberWood,
    tertiary = WarmGold,
    background = SandLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFF3EEE8),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF2C1E14),
    onSurface = Color(0xFF2C1E14),
    errorContainer = Color(0xFFFFDADA),
    onErrorContainer = Color(0xFF5E1E1E),
    primaryContainer = Color(0xFFF0E5D1),
    onPrimaryContainer = Color(0xFF2C1E14)
)

// Marine / Ocean Theme
private val MarineColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    secondary = Color(0xFF26A69A),
    tertiary = Color(0xFF40C4FF),
    background = Color(0xFF04121F),
    surface = Color(0xFF082035),
    surfaceVariant = Color(0xFF0F3252),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFE0F7FA),
    onSurface = Color(0xFFB2EBF2),
    primaryContainer = Color(0xFF0C2943),
    onPrimaryContainer = Color(0xFF00E5FF)
)

// Malachite / Emerald Gem Theme
private val MalachiteColorScheme = darkColorScheme(
    primary = Color(0xFF2ECC71),
    secondary = Color(0xFFFFD700),
    tertiary = Color(0xFF81C784),
    background = Color(0xFF04180E),
    surface = Color(0xFF092817),
    surfaceVariant = Color(0xFF103D24),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE8F5E9),
    onSurface = Color(0xFFC8E6C9),
    primaryContainer = Color(0xFF0E3822),
    onPrimaryContainer = Color(0xFF2ECC71)
)

// Cosmic Neon Theme
private val CosmicColorScheme = darkColorScheme(
    primary = Color(0xFF00FFCC), // Turquoise neon
    secondary = Color(0xFFFF007F), // Violet/pink neon
    tertiary = Color(0xFF8A2BE2), // Indigo accent
    background = Color(0xFF07050F), // Absolute Space dark
    surface = Color(0xFF131124), // Neon slate
    surfaceVariant = Color(0xFF211D36),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFECE9FC),
    onSurface = Color(0xFFE2DFF5),
    primaryContainer = Color(0xFF2F294E),
    onPrimaryContainer = Color(0xFF00FFCC)
)

// Psychedelic Theme (Cyberpunk + Gaudí + Van Gogh)
private val PsychedelicColorScheme = darkColorScheme(
    primary = Color(0xFFFF007F), // Cyberpunk Neon Magenta
    secondary = Color(0xFF00FFFF), // Electric Cyan
    tertiary = Color(0xFFFFB300), // Van Gogh Cadmium Yellow
    background = Color(0xFF120722), // Starry Night ultramarine/violet
    surface = Color(0xFF1E0C36),
    surfaceVariant = Color(0xFF331454),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFFFF8E1),
    onSurface = Color(0xFFFFE082),
    primaryContainer = Color(0xFF3D1460),
    onPrimaryContainer = Color(0xFF00FFFF)
)

// Black & White Theme
private val BwColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFF888888),
    tertiary = Color(0xFF333333),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFF2E2E2E),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    primaryContainer = Color(0xFF222222),
    onPrimaryContainer = Color.White
)

@Composable
fun MyApplicationTheme(
    themeId: String = "wood",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeId.lowercase()) {
        "marine", "water", "sea", "sapphire" -> MarineColorScheme
        "malachite", "emerald" -> MalachiteColorScheme
        "cosmic", "space" -> CosmicColorScheme
        "psychedelic", "rainbow", "cyberpunk" -> PsychedelicColorScheme
        "bw" -> BwColorScheme
        "wood", "warm", "vintage" -> if (darkTheme) DarkColorScheme else LightColorScheme
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
