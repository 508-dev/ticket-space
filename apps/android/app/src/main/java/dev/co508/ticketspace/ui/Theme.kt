package dev.co508.ticketspace.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val light =
    lightColorScheme(
        primary = Color(0xFF006B60),
        secondary = Color(0xFF805600),
        background = Color(0xFFF7FAF7),
        surface = Color(0xFFF7FAF7),
    )
private val dark = darkColorScheme(primary = Color(0xFF76D9C6), secondary = Color(0xFFF2C15F))

@Composable
fun TicketSpaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content)
}
