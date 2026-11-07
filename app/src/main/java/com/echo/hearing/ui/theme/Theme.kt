package com.echo.hearing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Slate950 = Color(0xFF030712)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)

val Cyan400 = Color(0xFF22D3EE)
val Sky500 = Color(0xFF0EA5E9)
val Emerald500 = Color(0xFF10B981)
val Amber500 = Color(0xFFF59E0B)
val Rose500 = Color(0xFFF43F5E)

private val EchoDarkColorScheme = darkColorScheme(
    primary = Sky500,
    onPrimary = Color.White,
    primaryContainer = Slate800,
    onPrimaryContainer = Cyan400,
    secondary = Cyan400,
    onSecondary = Slate950,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    error = Rose500,
    onError = Color.White
)

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EchoDarkColorScheme,
        content = content
    )
}
