package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = MintGreen,
    secondary = CoolIndigo,
    tertiary = GoldenSun,
    background = DarkDbBg,
    surface = DarkDbSurface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF8FAFC),
    outline = DarkDbBorder,
    surfaceVariant = DarkDbHover,
    onSurfaceVariant = Color(0xFF94A3B8)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CoolIndigo,
    secondary = MintGreen,
    tertiary = GoldenSun,
    background = LightDbBg,
    surface = LightDbSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = ProfTextDark,
    onSurface = ProfTextDark,
    outline = LightDbBorder,
    surfaceVariant = LightDbHover,
    onSurfaceVariant = ProfTextMuted
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
