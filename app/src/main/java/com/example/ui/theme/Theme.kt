package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CoolDarkPrimary,
    secondary = CoolDarkSecondary,
    tertiary = M3AmberAccent,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface
  )

private val LightColorScheme =
  lightColorScheme(
    primary = M3PurplePrimary,
    onPrimary = M3PurpleOnPrimary,
    primaryContainer = M3PurplePrimaryContainer,
    onPrimaryContainer = M3PurpleOnPrimaryContainer,
    secondary = M3PurpleSecondary,
    onSecondary = M3PurpleOnSecondary,
    secondaryContainer = M3PurpleSecondaryContainer,
    onSecondaryContainer = M3PurpleOnSecondaryContainer,
    tertiary = M3PurpleTertiary,
    onTertiary = M3PurpleOnTertiary,
    background = M3PurpleBackground,
    onBackground = M3PurpleOnBackground,
    surface = M3PurpleSurface,
    onSurface = M3PurpleOnSurface,
    surfaceVariant = M3PurpleSurfaceVariant,
    onSurfaceVariant = M3PurpleOnSurfaceVariant,
    outline = M3PurpleOutline,
    outlineVariant = M3PurpleOutlineVariant
  )

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default so our custom design is preserved
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
