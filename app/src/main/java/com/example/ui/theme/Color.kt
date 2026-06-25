package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Groww-Inspired original premium Palette
val MintGreen = Color(0xFF00D09C)      // Signature growth/success green (positive market shifts)
val CoolIndigo = Color(0xFF4F46E5)     // Mutual funds / secondary asset badge indigo
val DeepCoral = Color(0xFFFF5252)      // Elegant warning/loss coral red (negative market shifts)
val GoldenSun = Color(0xFFFFB000)       // High-end warm tactical gold (for alerts/ratings)

// Dark Theme Absolute Luxe Slate Shades
val DarkDbBg = Color(0xFF0A0F1D)        // Obsidian dark blue/black background
val DarkDbSurface = Color(0xFF131A2A)   // Glassmorphic deep card surface
val DarkDbBorder = Color(0xFF1E283C)    // Sleek border dividing lines (slate-700)
val DarkDbHover = Color(0xFF232E44)     // Interactive dark hovered/active state

// Light Theme Crisp Modern Shades
val LightDbBg = Color(0xFFF6F8FA)       // Super fresh slate/gray light page background
val LightDbSurface = Color(0xFFFFFFFF)  // Pure snowy white for cards
val LightDbBorder = Color(0xFFE5E9F0)   // Sophisticated divider borders (slate-100)
val LightDbHover = Color(0xFFEEF2F6)    // Soft touch states

// Primary functional mappings for high-contrast elite typography
val ProfTextDark = Color(0xFF0A101D)    // Dark slate headings
val ProfTextMuted = Color(0xFF64748B)   // Slate gray subtexts
val ProfWhite = Color(0xFFFFFFFF)

// Legacy compatibility values (mapped to new beautiful scheme)
val ProfBlue = CoolIndigo
val ProfLightBlue = Color(0xFFEEF2F6)
val ProfNavy = Color(0xFF0F172A)
val ProfBg = LightDbBg
val ProfSurface = LightDbSurface
val ProfTextDarkVal = ProfTextDark
val ProfBorder = LightDbBorder

val ProfGreen = MintGreen
val ProfRed = DeepCoral
val ProfAmber = GoldenSun

val CyberGreen = MintGreen
val CyberRed = DeepCoral
val CyberAmber = GoldenSun
val DarkSlateBg = DarkDbBg
val BoxSurface = DarkDbSurface
val TextPrimary = ProfWhite
val TextSecondary = ProfTextMuted
val AccentGold = GoldenSun
val SlateBlueLine = DarkDbBorder
