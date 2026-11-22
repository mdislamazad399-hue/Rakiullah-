package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyLog
import com.example.data.model.StudyMode
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SageTertiary
import com.example.ui.theme.TerracottaSecondary
import com.example.ui.theme.deckAccentPalette

@Composable
fun EditorialStudyHeroBanner(
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        primary,
                        primary.copy(alpha = 0.86f),
                        Color(0xFF284F8E),
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle atmospheric circles
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = h * 0.65f,
                center = Offset(w * 0.85f, h * 0.25f)
            )
            drawCircle(
                color = secondary.copy(alpha = 0.25f),
                radius = h * 0.35f,
                center = Offset(w * 0.14f, h * 0.9f)
            )

            // Tilted flashcard 1 (back)
            rotate(degrees = -10f, pivot = Offset(w * 0.72f, h * 0.52f)) {
                drawRoundRect(
                    color = AmberAccent.copy(alpha = 0.92f),
                    topLeft = Offset(w * 0.57f, h * 0.22f),
                    size = Size(w * 0.27f, h * 0.58f),
                    cornerRadius = CornerRadius(24f, 24f),
                )
            }

            // Tilted flashcard 2 (front)
            rotate(degrees = 6f, pivot = Offset(w * 0.78f, h * 0.52f)) {
                drawRoundRect(
                    color = surface,
                    topLeft = Offset(w * 0.64f, h * 0.18f),
                    size = Size(w * 0.28f, h * 0.62f),
                    cornerRadius = CornerRadius(24f, 24f),
                )
                // Card header stripe
                drawRoundRect(
                    color = tertiary,
                    topLeft = Offset(w * 0.67f, h * 0.28f),
                    size = Size(w * 0.14f, 12f),
                    cornerRadius = CornerRadius(6f, 6f),
                )
                // Card lines
                drawRoundRect(
                    color = primary.copy(alpha = 0.22f),
                    topLeft = Offset(w * 0.67f, h * 0.42f),
                    size = Size(w * 0.21f, 10f),
                    cornerRadius = CornerRadius(5f, 5f),
                )
                drawRoundRect(
                    color = primary.copy(alpha = 0.15f),
                    topLeft = Offset(w * 0.67f, h * 0.53f),
                    size = Size(w * 0.16f, 10f),
                    cornerRadius = CornerRadius(5f, 5f),
                )
            }

            // Spark star accent
            val starCenter = Offset(w * 0.55f, h * 0.22f)
            val starPath = Path().apply {
                moveTo(starCenter.x, starCenter.y - 18f)
                lineTo(starCenter.x + 5f, starCenter.y - 5f)
                lineTo(starCenter.x + 18f, starCenter.y)
                lineTo(starCenter.x + 5f, starCenter.y + 5f)
                lineTo(starCenter.x, starCenter.y + 18f)
                lineTo(starCenter.x - 5f, starCenter.y + 5f)
                lineTo(starCenter.x - 18f, starCenter.y)
                lineTo(starCenter.x - 5f, starCenter.y - 5f)
                close()
            }
            drawPath(starPath, color = Color(0xFFFFD166))
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.6f)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = "TEACHER & STUDENT STUDIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFE9C7),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Text(
                text = "Interactive Study & Mastery",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
            )
            Text(
                text = "Flashcards, quizzes & live class analytics.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
            )
        }
    }
}

@Composable
fun EmptyStateIllustration(
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    val variant = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier.size(120.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = accentColor.copy(alpha = 0.12f),
            radius = w * 0.44f,
            center = Offset(w / 2f, h / 2f),
        )
        rotate(degrees = -8f, pivot = Offset(w * 0.45f, h * 0.55f)) {
            drawRoundRect(
                color = variant,
                topLeft = Offset(w * 0.24f, h * 0.26f),
                size = Size(w * 0.46f, h * 0.54f),
                cornerRadius = CornerRadius(20f, 20f),
            )
        }
        rotate(degrees = 6f, pivot = Offset(w * 0.55f, h * 0.52f)) {
            drawRoundRect(
                color = surface,
                topLeft = Offset(w * 0.30f, h * 0.22f),
                size = Size(w * 0.46f, h * 0.54f),
                cornerRadius = CornerRadius(20f, 20f),
            )
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(w * 0.36f, h * 0.32f),
                size = Size(w * 0.22f, 10f),
                cornerRadius = CornerRadius(5f, 5f),
            )
            drawRoundRect(
                color = accentColor.copy(alpha = 0.25f),
                topLeft = Offset(w * 0.36f, h * 0.44f),
                size = Size(w * 0.32f, 8f),
                cornerRadius = CornerRadius(4f, 4f),
            )
            drawRoundRect(
                color = accentColor.copy(alpha = 0.18f),
                topLeft = Offset(w * 0.36f, h * 0.54f),
                size = Size(w * 0.26f, 8f),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }
    }
}

@Composable
fun MasteryRingChart(
    progress: Float,
    centerLabel: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "masteryRing",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(136.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 14.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AccuracyBarChart(
    logs: List<StudyLog>,
    modifier: Modifier = Modifier,
) {
    val recent = logs.take(7).reversed()
    val quizColor = IndigoPrimary
    val cardColor = SageTertiary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Horizontal guide lines (50%, 100%)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * 0.5f),
                    end = Offset(w, h * 0.5f),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, 4f),
                    end = Offset(w, 4f),
                    strokeWidth = 2f,
                )
                if (recent.isNotEmpty()) {
                    val slotWidth = w / recent.size.toFloat()
                    val barWidth = (slotWidth * 0.48f).coerceAtMost(44.dp.toPx())
                    recent.forEachIndexed { idx, log ->
                        val centerX = slotWidth * idx + slotWidth / 2f
                        val left = centerX - barWidth / 2f
                        // Background track
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 4f),
                            size = Size(barWidth, h - 8f),
                            cornerRadius = CornerRadius(14f, 14f),
                        )
                        val pct = (log.scorePercent.coerceIn(0L, 100L) / 100f)
                        val barHeight = ((h - 8f) * pct).coerceAtLeast(10f)
                        val barColor = if (StudyMode.fromRaw(log.mode) == StudyMode.QUIZ) {
                            quizColor
                        } else {
                            cardColor
                        }
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, h - 4f - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(14f, 14f),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            recent.forEach { log ->
                Text(
                    text = "${log.scorePercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendChip(color = IndigoPrimary, label = "Quiz")
            LegendChip(color = SageTertiary, label = "Flashcards")
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SubjectBadge(
    subject: String,
    accentKey: String,
    modifier: Modifier = Modifier,
) {
    val palette = deckAccentPalette(accentKey, isSystemInDarkTheme())
    Surface(
        modifier = modifier,
        color = palette.container,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = subject.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun JoinCodePill(
    joinCode: String,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onCopyClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "CODE: $joinCode",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "Copy join code",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
