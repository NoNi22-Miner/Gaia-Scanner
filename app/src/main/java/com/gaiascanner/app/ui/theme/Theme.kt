package com.gaiascanner.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2CE),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6358),
    background = Color(0xFFFBFEFB),
    surface = Color(0xFFFBFEFB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD6B3),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFA6F2CE),
    secondary = Color(0xFFB3CCBF),
    background = Color(0xFF191C1A),
    surface = Color(0xFF191C1A),
)

@Composable
fun GaiaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
