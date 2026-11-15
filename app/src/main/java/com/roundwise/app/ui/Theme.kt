package com.roundwise.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RW {
    val bg = Color(0xFF0E1116)
    val surface = Color(0xFF171B22)
    val surface2 = Color(0xFF1F242D)
    val line = Color(0xFF2A303B)
    val text = Color(0xFFE8ECF2)
    val sub = Color(0xFF9AA3B2)
    val outbound = Color(0xFFFFB547)   // amber — 가는 길
    val inbound = Color(0xFF3DD6C6)    // teal — 돌아오는 길
    val glare = Color(0xFFFF5D5D)      // red — 역광
    val crowd = Color(0xFFB57CFF)      // violet — 인파
    val queue = Color(0xFF6EA8FF)      // blue — 출차 대기
    val walk = Color(0xFF8BD17C)       // green — 도보
    val good = Color(0xFF52D38A)
    val muted = Color(0xFF4A5261)
}

@Composable
fun RoundwiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RW.inbound,
            secondary = RW.outbound,
            background = RW.bg,
            surface = RW.surface,
            onSurface = RW.text,
            onBackground = RW.text,
        ),
        content = content,
    )
}
