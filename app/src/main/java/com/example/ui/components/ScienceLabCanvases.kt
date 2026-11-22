package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.lab.OhmsLawMetrics
import com.example.lab.PendulumMetrics
import com.example.lab.ScienceLabEngine
import com.example.lab.SensorTelemetry
import com.example.lab.TitrationIndicator
import com.example.lab.TitrationMetrics
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val LabGridBg = Color(0xFF0A1220)
private val LabGridLine = Color(0xFF1A2942)
private val WireCopper = Color(0xFF4CC9F0)
private val WireInactive = Color(0xFF475569)
private val ElectronCyan = Color(0xFF72EFDD)

private fun DrawScope.drawBenchGrid(stepFraction: Float = 0.08f) {
    drawRect(color = LabGridBg)
    val step = (size.minDimension * stepFraction).coerceAtLeast(18f)
    var x = step
    while (x < size.width) {
        drawLine(LabGridLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(LabGridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

@Composable
fun OhmsLawCircuitCanvas(
    metrics: OhmsLawMetrics,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val infiniteTransition = rememberInfiniteTransition(label = "electronFlow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "electronPhase",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawBenchGrid()

        val left = size.width * 0.14f
        val right = size.width * 0.62f
        val top = size.height * 0.22f
        val bottom = size.height * 0.78f
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f

        val wireColor = if (metrics.switchClosed) WireCopper else WireInactive
        val wireWidth = 5f

        // Top wire (broken at switch)
        val swStart = left + (right - left) * 0.22f
        val swEnd = left + (right - left) * 0.42f
        drawLine(wireColor, Offset(left, top), Offset(swStart, top), strokeWidth = wireWidth, cap = StrokeCap.Round)
        drawLine(wireColor, Offset(swEnd, top), Offset(right, top), strokeWidth = wireWidth, cap = StrokeCap.Round)

        // Switch blade
        val bladeEnd = if (metrics.switchClosed) {
            Offset(swEnd, top)
        } else {
            Offset(swEnd - (swEnd - swStart) * 0.22f, top - size.height * 0.10f)
        }
        drawLine(
            color = if (metrics.switchClosed) Color(0xFF4ADE80) else Color(0xFFF87171),
            start = Offset(swStart, top),
            end = bladeEnd,
            strokeWidth = 6f,
            cap = StrokeCap.Round,
        )
        drawCircle(Color.White, radius = 6f, center = Offset(swStart, top))
        drawCircle(Color.White, radius = 6f, center = Offset(swEnd, top))

        // Right wire + Bulb
        drawLine(wireColor, Offset(right, top), Offset(right, bottom), strokeWidth = wireWidth, cap = StrokeCap.Round)
        // Bottom wire + Resistor
        drawLine(wireColor, Offset(right, bottom), Offset(left, bottom), strokeWidth = wireWidth, cap = StrokeCap.Round)
        // Left wire + Battery
        drawLine(wireColor, Offset(left, bottom), Offset(left, top), strokeWidth = wireWidth, cap = StrokeCap.Round)

        // Battery cell on left vertical wire
        drawRect(
            color = LabGridBg,
            topLeft = Offset(left - 24f, midY - 26f),
            size = Size(48f, 52f),
        )
        drawLine(Color(0xFFFBBF24), Offset(left - 22f, midY - 12f), Offset(left + 22f, midY - 12f), strokeWidth = 6f)
        drawLine(Color(0xFF94A3B8), Offset(left - 12f, midY + 12f), Offset(left + 12f, midY + 12f), strokeWidth = 6f)

        // Resistor box on bottom horizontal wire
        val resW = (right - left) * 0.34f
        val resH = 26f
        val resLeft = midX - resW / 2f
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(resLeft, bottom - resH / 2f),
            size = Size(resW, resH),
            cornerRadius = CornerRadius(8f, 8f),
        )
        drawRoundRect(
            color = Color(0xFFF59E0B),
            topLeft = Offset(resLeft, bottom - resH / 2f),
            size = Size(resW, resH),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 3f),
        )
        // Color bands inside resistor
        val bandXs = listOf(0.25f, 0.50f, 0.75f)
        val bandColors = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFFFBBF24))
        bandXs.forEachIndexed { idx, frac ->
            val bx = resLeft + resW * frac
            drawLine(bandColors[idx], Offset(bx, bottom - resH / 2f + 2f), Offset(bx, bottom + resH / 2f - 2f), strokeWidth = 5f)
        }

        // Glowing lamp on right vertical wire
        val bulbRadius = size.minDimension * 0.085f
        val bulbCenter = Offset(right, midY)
        if (metrics.bulbBrightness > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFDE047).copy(alpha = (0.75f * metrics.bulbBrightness).coerceIn(0.15f, 0.85f)),
                        Color(0xFFF59E0B).copy(alpha = 0.2f * metrics.bulbBrightness),
                        Color.Transparent,
                    ),
                    center = bulbCenter,
                    radius = bulbRadius * 2.6f,
                ),
                center = bulbCenter,
                radius = bulbRadius * 2.6f,
            )
        }
        drawCircle(
            color = if (metrics.switchClosed) Color(0xFFFEF08A).copy(alpha = 0.25f + 0.7f * metrics.bulbBrightness) else Color(0xFF1E293B),
            radius = bulbRadius,
            center = bulbCenter,
        )
        drawCircle(
            color = Color(0xFFE2E8F0),
            radius = bulbRadius,
            center = bulbCenter,
            style = Stroke(width = 3f),
        )

        // Drifting electrons along perimeter when closed
        if (metrics.switchClosed && metrics.currentAmps > 0.001f) {
            val speedFactor = (metrics.currentAmps * 2.5f).coerceIn(0.5f, 4.0f)
            val loopOffset = (phase * speedFactor) % 1f
            val perimeterPts = listOf(
                Offset(left, top),
                Offset(right, top),
                Offset(right, bottom),
                Offset(left, bottom),
            )
            val electronCount = 14
            for (i in 0 until electronCount) {
                val t = (i.toFloat() / electronCount + loopOffset) % 1f
                val pos = interpolateLoop(perimeterPts, t)
                drawCircle(ElectronCyan, radius = 5f, center = pos)
            }
        }

        // Right-side Live V-I Characteristic Curve
        val plotLeft = size.width * 0.71f
        val plotRight = size.width * 0.94f
        val plotTop = size.height * 0.20f
        val plotBottom = size.height * 0.80f
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(plotLeft - 8f, plotTop - 8f),
            size = Size(plotRight - plotLeft + 16f, plotBottom - plotTop + 16f),
            cornerRadius = CornerRadius(12f, 12f),
        )
        drawLine(Color(0xFF94A3B8), Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), strokeWidth = 2f)
        drawLine(Color(0xFF94A3B8), Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), strokeWidth = 2f)

        val maxI = 1.2f // Amps scale cap for clear graph slope
        val endV = 24f
        val endI = (endV / metrics.resistanceOhm).coerceAtMost(maxI)
        val endX = plotLeft + (endV / 24f) * (plotRight - plotLeft)
        val endY = plotBottom - (endI / maxI) * (plotBottom - plotTop)
        drawLine(Color(0xFF38BDF8), Offset(plotLeft, plotBottom), Offset(endX, endY), strokeWidth = 4f)

        // Current operating dot
        val curX = plotLeft + (metrics.voltageV / 24f) * (plotRight - plotLeft)
        val curY = plotBottom - (metrics.currentAmps.coerceAtMost(maxI) / maxI) * (plotBottom - plotTop)
        drawCircle(
            color = if (metrics.switchClosed) Color(0xFF4ADE80) else Color(0xFFF87171),
            radius = 7f,
            center = Offset(curX, curY),
        )

        drawLabel(textMeasurer, "${String.format("%.1f", metrics.voltageV)} V", Offset(left - 18f, top - 28f), Color(0xFFFBBF24))
        drawLabel(textMeasurer, "${metrics.resistanceOhm.toInt()} Ω", Offset(midX - 18f, bottom + 20f), Color(0xFFF59E0B))
        drawLabel(textMeasurer, "I vs V", Offset(plotLeft, plotTop - 26f), Color(0xFF94A3B8))
    }
}

private fun interpolateLoop(corners: List<Offset>, t: Float): Offset {
    val seg = (t * 4f).toInt().coerceIn(0, 3)
    val localT = (t * 4f) - seg
    val a = corners[seg]
    val b = corners[(seg + 1) % 4]
    return Offset(
        a.x + (b.x - a.x) * localT,
        a.y + (b.y - a.y) * localT,
    )
}

@Composable
fun TitrationLabCanvas(
    metrics: TitrationMetrics,
    indicator: TitrationIndicator,
    isDripping: Boolean,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val targetLiquidColor = Color(metrics.liquidColorArgb)
    val liquidColor by animateColorAsState(
        targetValue = targetLiquidColor,
        animationSpec = tween(durationMillis = 320),
        label = "titrationColor",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "dripTransition")
    val dropY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dropFall",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawBenchGrid()

        // Left: Burette + Erlenmeyer Flask
        val centerX = size.width * 0.27f
        val buretteTop = size.height * 0.08f
        val buretteBottom = size.height * 0.44f
        val buretteW = 24f

        // Burette body
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(centerX - buretteW / 2f, buretteTop),
            size = Size(buretteW, buretteBottom - buretteTop),
            cornerRadius = CornerRadius(6f, 6f),
        )
        // NaOH remaining level in burette
        val remainingFraction = (1f - (metrics.baseAddedMl / 60f)).coerceIn(0.08f, 0.95f)
        val fillH = (buretteBottom - buretteTop - 10f) * remainingFraction
        drawRect(
            color = Color(0x8838BDF8),
            topLeft = Offset(centerX - buretteW / 2f + 3f, buretteBottom - fillH - 4f),
            size = Size(buretteW - 6f, fillH),
        )
        drawRoundRect(
            color = Color(0xFFCBD5E1),
            topLeft = Offset(centerX - buretteW / 2f, buretteTop),
            size = Size(buretteW, buretteBottom - buretteTop),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 2.5f),
        )
        // Nozzle tip
        drawLine(Color(0xFFCBD5E1), Offset(centerX, buretteBottom), Offset(centerX, buretteBottom + 14f), strokeWidth = 4f)

        // Falling drop when dripping or just added
        if (isDripping) {
            val fallTop = buretteBottom + 16f
            val fallBottom = size.height * 0.74f
            val cy = fallTop + (fallBottom - fallTop) * dropY
            drawCircle(Color(0xFF38BDF8), radius = 4.5f, center = Offset(centerX, cy))
        }

        // Conical Flask
        val flaskTopY = size.height * 0.56f
        val flaskBottomY = size.height * 0.88f
        val neckHalfW = 18f
        val baseHalfW = size.width * 0.13f

        val flaskPath = Path().apply {
            moveTo(centerX - neckHalfW, flaskTopY)
            lineTo(centerX + neckHalfW, flaskTopY)
            lineTo(centerX + neckHalfW, flaskTopY + 26f)
            lineTo(centerX + baseHalfW, flaskBottomY - 12f)
            quadraticTo(centerX + baseHalfW, flaskBottomY, centerX + baseHalfW - 14f, flaskBottomY)
            lineTo(centerX - baseHalfW + 14f, flaskBottomY)
            quadraticTo(centerX - baseHalfW, flaskBottomY, centerX - baseHalfW, flaskBottomY - 12f)
            lineTo(centerX - neckHalfW, flaskTopY + 26f)
            close()
        }

        // Liquid fill inside lower conical flask
        val fillLevelY = flaskBottomY - (flaskBottomY - flaskTopY) * (0.44f + 0.18f * (metrics.baseAddedMl / 60f)).coerceAtMost(0.72f)
        val liquidWidthRatio = (flaskBottomY - fillLevelY) / (flaskBottomY - flaskTopY - 26f)
        val topLiquidHalfW = baseHalfW - (baseHalfW - neckHalfW) * liquidWidthRatio
        val liquidPath = Path().apply {
            moveTo(centerX - topLiquidHalfW, fillLevelY)
            lineTo(centerX + topLiquidHalfW, fillLevelY)
            lineTo(centerX + baseHalfW, flaskBottomY - 12f)
            quadraticTo(centerX + baseHalfW, flaskBottomY, centerX + baseHalfW - 14f, flaskBottomY)
            lineTo(centerX - baseHalfW + 14f, flaskBottomY)
            quadraticTo(centerX - baseHalfW, flaskBottomY, centerX - baseHalfW, flaskBottomY - 12f)
            close()
        }
        drawPath(liquidPath, color = liquidColor)
        drawPath(flaskPath, color = Color(0xFFE2E8F0), style = Stroke(width = 3f, join = StrokeJoin.Round))

        // Right: Live pH vs Volume Titration Curve
        val plotLeft = size.width * 0.52f
        val plotRight = size.width * 0.94f
        val plotTop = size.height * 0.16f
        val plotBottom = size.height * 0.84f

        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(plotLeft - 8f, plotTop - 8f),
            size = Size(plotRight - plotLeft + 16f, plotBottom - plotTop + 16f),
            cornerRadius = CornerRadius(12f, 12f),
        )
        // pH 7 neutral dashed reference
        val neutralY = plotBottom - (7f / 14f) * (plotBottom - plotTop)
        drawLine(Color(0xFF334155), Offset(plotLeft, neutralY), Offset(plotRight, neutralY), strokeWidth = 2f)
        drawLine(Color(0xFF94A3B8), Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), strokeWidth = 2f)
        drawLine(Color(0xFF94A3B8), Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), strokeWidth = 2f)

        val maxVol = 60f
        val curvePath = Path()
        val steps = 60
        for (i in 0..steps) {
            val v = (i.toFloat() / steps) * maxVol
            val pt = ScienceLabEngine.computeTitration(
                acidMolarity = metrics.acidMolarity,
                acidVolumeMl = metrics.acidVolumeMl,
                baseMolarity = metrics.baseMolarity,
                baseAddedMl = v,
                indicator = indicator,
            )
            val px = plotLeft + (v / maxVol) * (plotRight - plotLeft)
            val py = plotBottom - (pt.pH / 14f) * (plotBottom - plotTop)
            if (i == 0) curvePath.moveTo(px, py) else curvePath.lineTo(px, py)
        }
        drawPath(curvePath, color = Color(0xFF38BDF8), style = Stroke(width = 4f, cap = StrokeCap.Round))

        val curX = plotLeft + (metrics.baseAddedMl.coerceIn(0f, maxVol) / maxVol) * (plotRight - plotLeft)
        val curY = plotBottom - (metrics.pH / 14f) * (plotBottom - plotTop)
        drawCircle(Color(0xFFF472B6), radius = 8f, center = Offset(curX, curY))
        drawCircle(Color.White, radius = 3.5f, center = Offset(curX, curY))

        drawLabel(textMeasurer, "pH ${String.format("%.2f", metrics.pH)}", Offset(plotLeft, plotTop - 28f), Color(0xFFF8FAFC))
        drawLabel(textMeasurer, "pH 7", Offset(plotLeft + 4f, neutralY - 20f), Color(0xFF64748B))
    }
}

@Composable
fun PendulumLabCanvas(
    metrics: PendulumMetrics,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        drawBenchGrid()

        val pivot = Offset(size.width * 0.42f, size.height * 0.14f)
        // Beam
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(pivot.x - 60f, pivot.y),
            end = Offset(pivot.x + 60f, pivot.y),
            strokeWidth = 6f,
            cap = StrokeCap.Round,
        )

        // Protractor arc
        val arcR = size.minDimension * 0.22f
        drawArc(
            color = Color(0xFF334155),
            startAngle = 90f - metrics.releaseAngleDeg,
            sweepAngle = metrics.releaseAngleDeg * 2f,
            useCenter = false,
            topLeft = Offset(pivot.x - arcR, pivot.y - arcR),
            size = Size(arcR * 2f, arcR * 2f),
            style = Stroke(width = 3f),
        )
        // Equilibrium vertical reference
        drawLine(
            color = Color(0xFF334155),
            start = pivot,
            end = Offset(pivot.x, size.height * 0.88f),
            strokeWidth = 2f,
        )

        // Map length [0.2m .. 3.0m] to pixel rope length
        val minRope = size.height * 0.28f
        val maxRope = size.height * 0.72f
        val lenFraction = ((metrics.lengthMeters - 0.2f) / 2.8f).coerceIn(0f, 1f)
        val ropePx = minRope + (maxRope - minRope) * lenFraction

        val rad = metrics.currentAngleDeg * PI.toFloat() / 180f
        val bobPos = Offset(
            x = pivot.x + ropePx * sin(rad),
            y = pivot.y + ropePx * cos(rad),
        )

        drawLine(Color(0xFFE2E8F0), pivot, bobPos, strokeWidth = 3.5f)
        drawCircle(Color(0xFFF59E0B), radius = 6f, center = pivot)

        // Bob glow & sphere
        val bobRadius = 20f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8)),
                center = Offset(bobPos.x - 5f, bobPos.y - 5f),
                radius = bobRadius * 1.3f,
            ),
            radius = bobRadius,
            center = bobPos,
        )
        drawCircle(Color.White.copy(alpha = 0.7f), radius = bobRadius, center = bobPos, style = Stroke(width = 2f))

        // Right side: Live KE vs PE Energy Split Bars
        val totalE = (metrics.kineticEnergyJ + metrics.potentialEnergyJ).coerceAtLeast(0.001f)
        val keFrac = (metrics.kineticEnergyJ / totalE).coerceIn(0f, 1f)
        val peFrac = (metrics.potentialEnergyJ / totalE).coerceIn(0f, 1f)

        val barBaseY = size.height * 0.80f
        val barMaxH = size.height * 0.56f
        val keX = size.width * 0.78f
        val peX = size.width * 0.88f
        val barW = 24f

        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(keX, barBaseY - barMaxH),
            size = Size(barW, barMaxH),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = Color(0xFF22C55E),
            topLeft = Offset(keX, barBaseY - barMaxH * keFrac),
            size = Size(barW, barMaxH * keFrac),
            cornerRadius = CornerRadius(6f, 6f),
        )

        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(peX, barBaseY - barMaxH),
            size = Size(barW, barMaxH),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = Color(0xFFF59E0B),
            topLeft = Offset(peX, barBaseY - barMaxH * peFrac),
            size = Size(barW, barMaxH * peFrac),
            cornerRadius = CornerRadius(6f, 6f),
        )

        drawLabel(textMeasurer, "KE", Offset(keX + 2f, barBaseY + 6f), Color(0xFF4ADE80))
        drawLabel(textMeasurer, "PE", Offset(peX + 2f, barBaseY + 6f), Color(0xFFFBBF24))
        drawLabel(textMeasurer, "θ=${String.format("%.1f", metrics.currentAngleDeg)}°", Offset(16f, 14f), Color(0xFFE2E8F0))
    }
}

@Composable
fun SensorOscilloscopeCanvas(
    telemetry: SensorTelemetry,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        drawBenchGrid()

        // Left: Live Oscilloscope Waveform for |a| (m/s^2)
        val plotLeft = size.width * 0.06f
        val plotRight = size.width * 0.68f
        val plotTop = size.height * 0.16f
        val plotBottom = size.height * 0.84f

        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(plotLeft, plotTop),
            size = Size(plotRight - plotLeft, plotBottom - plotTop),
            cornerRadius = CornerRadius(12f, 12f),
        )
        val midY = (plotTop + plotBottom) / 2f
        drawLine(Color(0xFF1E3A5F), Offset(plotLeft, midY), Offset(plotRight, midY), strokeWidth = 2f)

        val samples = telemetry.waveformHistory
        if (samples.size >= 2) {
            val wavePath = Path()
            val maxAccel = 22f
            samples.forEachIndexed { idx, value ->
                val x = plotLeft + (idx.toFloat() / (samples.size - 1).coerceAtLeast(1)) * (plotRight - plotLeft)
                val normalized = (value / maxAccel).coerceIn(0f, 1f)
                val y = plotBottom - normalized * (plotBottom - plotTop)
                if (idx == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            drawPath(wavePath, color = Color(0xFF34D399), style = Stroke(width = 4f, cap = StrokeCap.Round))
        }

        // Right: Live Magnetometer Compass Vector Dial
        val dialCenter = Offset(size.width * 0.84f, size.height * 0.50f)
        val dialRadius = (size.minDimension * 0.28f).coerceAtLeast(36f)
        drawCircle(Color(0xFF0F172A), radius = dialRadius, center = dialCenter)
        drawCircle(Color(0xFF475569), radius = dialRadius, center = dialCenter, style = Stroke(width = 3f))

        val angleRad = if (telemetry.hasMagnetometer) {
            atan2(telemetry.magY.toDouble(), telemetry.magX.toDouble()).toFloat()
        } else {
            (-PI / 2.0).toFloat()
        }
        val tip = Offset(
            dialCenter.x + (dialRadius * 0.72f) * cos(angleRad),
            dialCenter.y + (dialRadius * 0.72f) * sin(angleRad),
        )
        drawLine(
            color = if (telemetry.hasMagnetometer) Color(0xFFF43F5E) else Color(0xFF64748B),
            start = dialCenter,
            end = tip,
            strokeWidth = 5f,
            cap = StrokeCap.Round,
        )
        drawCircle(Color.White, radius = 5f, center = dialCenter)

        val accelLabel = if (telemetry.hasAccelerometer) {
            "|a| = ${String.format("%.2f", telemetry.accelMagnitude)} m/s²"
        } else {
            "Accelerometer unavailable"
        }
        drawLabel(textMeasurer, accelLabel, Offset(plotLeft + 8f, plotTop - 26f), Color(0xFF6EE7B7))
        drawLabel(textMeasurer, "B Field", Offset(dialCenter.x - 24f, dialCenter.y + dialRadius + 8f), Color(0xFF94A3B8))
    }
}

private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    color: Color,
) {
    val result = measurer.measure(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
    drawText(result, topLeft = topLeft)
}
