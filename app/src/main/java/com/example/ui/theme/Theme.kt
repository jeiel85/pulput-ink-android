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

private val ElegantDarkColorScheme = darkColorScheme(
  primary = ElegantDarkPrimary,
  onPrimary = ElegantDarkOnPrimary,
  background = ElegantDarkBackground,
  onBackground = ElegantDarkOnBackground,
  surface = ElegantDarkSurface,
  onSurface = ElegantDarkOnSurface,
  secondary = ElegantDarkSecondary,
  onSecondary = ElegantDarkOnSecondary,
  outline = ElegantDarkOutline,
  surfaceVariant = ElegantDarkSurface,
  onSurfaceVariant = ElegantDarkOnSurfaceVariant
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = ElegantDarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
