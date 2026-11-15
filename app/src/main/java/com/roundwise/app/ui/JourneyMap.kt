package com.roundwise.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.roundwise.app.engine.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Everything the schematic map needs, independent of mode. */
data class MapModel(
    val corridors: List<Corridor>,
    val outbound: LegResult?,
    val inbound: LegResult?,
    val home: Place,
    val destination: Pair<String, LatLng>,
    val lots: List<ParkingLot>,
    val chosenLot: ParkingLot?,
    val hotspots: List<CrowdHotspot>,
    val sun: SunPosition?,
    val sunLabel: String,
    val extraPoint: LatLng? = null,
)

@Composable
fun JourneyMap(model: MapModel, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(RW.surface)) {
        Canvas(Modifier.matchParentSize()) { drawJourney(model) }
    }
}

private fun DrawScope.drawJourney(m: MapModel) {
    val all = m.corridors.flatMap { it.points } + m.home.location + m.destination.second +
        m.lots.map { it.location } + listOfNotNull(m.extraPoint)
    val minLat = all.minOf { it.lat }; val maxLat = all.maxOf { it.lat }
    val minLon = all.minOf { it.lon }; val maxLon = all.maxOf { it.lon }
    val kx = cos(Math.toRadians((minLat + maxLat) / 2))
    val w = (maxLon - minLon) * kx
    val h = (maxLat - minLat)
    val pad = 36f
    val scale = min((size.width - 2 * pad) / w.toFloat(), (size.height - 2 * pad - 20f) / h.toFloat())
    val ox = (size.width - w.toFloat() * scale) / 2
    val oy = (size.height - h.toFloat() * scale) / 2 + 8f
    fun p(l: LatLng) = Offset(ox + ((l.lon - minLon) * kx).toFloat() * scale, oy + ((maxLat - l.lat)).toFloat() * scale)
    fun pathOf(pts: List<LatLng>) = Path().apply { pts.forEachIndexed { i, l -> val o = p(l); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }

    // crowd hotspots
    m.hotspots.forEach { hs ->
        val c = p(hs.location)
        val r = (hs.radiusKm / 111.0 * 1.6).toFloat() * scale
        drawCircle(Brush.radialGradient(listOf(RW.crowd.copy(alpha = 0.45f), Color.Transparent), c, r), r, c)
    }
    // all corridors (dim)
    m.corridors.forEach { drawPath(pathOf(it.points), RW.line, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }

    fun leg(l: LegResult?, color: Color, offset: Float) {
        l ?: return
        val pts = l.corridor.points
        val path = pathOf(pts)
        drawPath(path, color, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round,
            pathEffect = if (offset > 0) PathEffect.dashPathEffect(floatArrayOf(18f, 10f)) else null))
        l.crowdSegments.forEach { s -> drawLine(RW.crowd, p(s.from), p(s.to), 13f, StrokeCap.Round, alpha = 0.55f + 0.45f * s.intensity.toFloat()) }
        l.glareSegments.forEach { s -> drawLine(RW.glare, p(s.from), p(s.to), 9f, StrokeCap.Round, alpha = 0.5f + 0.5f * s.intensity.toFloat()) }
    }
    if (m.outbound != null && m.inbound != null && m.outbound.corridor.id == m.inbound.corridor.id) {
        leg(m.outbound, RW.outbound, 0f); leg(m.inbound, RW.inbound, 1f)
    } else {
        leg(m.outbound, RW.outbound, 0f); leg(m.inbound, RW.inbound, 0f)
    }

    // lots
    m.lots.forEach { lot ->
        val c = p(lot.location)
        val chosen = lot.id == m.chosenLot?.id
        drawRect(if (chosen) RW.inbound else RW.sub, Offset(c.x - 9f, c.y - 9f), Size(18f, 18f))
        label("P", c + Offset(0f, 6f), RW.bg, 15f, bold = true)
    }
    // places
    val hp = p(m.home.location)
    drawCircle(RW.text, 11f, hp); drawCircle(RW.bg, 5f, hp)
    label(m.home.name, hp + Offset(0f, 30f), RW.text, 22f)
    val dp = p(m.destination.second)
    drawCircle(RW.outbound, 12f, dp)
    label(m.destination.first, dp + Offset(0f, -20f), RW.text, 22f)
    m.extraPoint?.let { drawCircle(RW.queue, 9f, p(it)) }

    // sun compass
    m.sun?.let { sun ->
        val c = Offset(size.width - 70f, 70f)
        drawCircle(RW.surface2, 50f, c)
        drawCircle(RW.line, 50f, c, style = Stroke(2f))
        label("N", c + Offset(0f, -32f), RW.sub, 16f)
        if (sun.elevationDeg > 0) {
            val a = Math.toRadians(sun.azimuthDeg)
            val tip = c + Offset((sin(a) * 40).toFloat(), (-cos(a) * 40).toFloat())
            drawLine(RW.outbound, c, tip, 4f, StrokeCap.Round)
            drawCircle(RW.outbound, 8f, tip)
        } else label("☾", c + Offset(0f, 8f), RW.sub, 28f)
        label(m.sunLabel, c + Offset(0f, 72f), RW.sub, 17f)
    }
}

private fun DrawScope.label(text: String, at: Offset, color: Color, sizePx: Float, bold: Boolean = false) {
    val paint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = sizePx
        this.color = color.toArgb()
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    drawContext.canvas.nativeCanvas.drawText(text, at.x, at.y, paint)
}

