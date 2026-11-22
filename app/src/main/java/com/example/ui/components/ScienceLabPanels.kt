package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lab.GravityPreset
import com.example.lab.LabObservation
import com.example.lab.OhmsLawMetrics
import com.example.lab.PendulumMetrics
import com.example.lab.SensorTelemetry
import com.example.lab.TitrationIndicator
import com.example.lab.TitrationMetrics
import com.example.ui.viewmodel.AiLabState
import java.util.Locale

@Composable
fun OhmsLawControlsCard(
    metrics: OhmsLawMetrics,
    onVoltageChange: (Float) -> Unit,
    onResistanceChange: (Float) -> Unit,
    onToggleSwitch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabMetricPill("Current (I)", "${String.format(Locale.US, "%.3f", metrics.currentAmps)} A", Modifier.weight(1f))
            LabMetricPill("Power (P)", "${String.format(Locale.US, "%.2f", metrics.powerWatts)} W", Modifier.weight(1f))
            LabMetricPill("Heat / 1m", "${metrics.heatPerMinuteJoules.toInt()} J", Modifier.weight(1f))
        }

        Text("Battery Voltage: ${String.format(Locale.US, "%.1f", metrics.voltageV)} V", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = metrics.voltageV,
            onValueChange = onVoltageChange,
            valueRange = 1f..24f,
            modifier = Modifier.testTag("ohm_voltage_slider"),
        )

        Text("Load Resistance: ${metrics.resistanceOhm.toInt()} Ω", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = metrics.resistanceOhm,
            onValueChange = onResistanceChange,
            valueRange = 5f..200f,
            modifier = Modifier.testTag("ohm_resistance_slider"),
        )

        FilledTonalButton(
            onClick = onToggleSwitch,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("ohm_switch_button"),
        ) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (metrics.switchClosed) "Circuit Switch: CLOSED (Live)" else "Circuit Switch: OPEN")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitrationControlsCard(
    metrics: TitrationMetrics,
    indicator: TitrationIndicator,
    isDripping: Boolean,
    onAddVolume: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onAcidMolarityChange: (Float) -> Unit,
    onBaseMolarityChange: (Float) -> Unit,
    onSelectIndicator: (TitrationIndicator) -> Unit,
    onToggleDrip: () -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabMetricPill("Flask pH", String.format(Locale.US, "%.2f", metrics.pH), Modifier.weight(1f))
            LabMetricPill("NaOH Added", "${String.format(Locale.US, "%.1f", metrics.baseAddedMl)} mL", Modifier.weight(1f))
            LabMetricPill("Eq. Point", "${String.format(Locale.US, "%.1f", metrics.equivalenceVolumeMl)} mL", Modifier.weight(1f))
        }

        Text(
            text = "${metrics.solutionLabel} • ${indicator.rangeNote}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TitrationIndicator.entries.forEach { option ->
                FilterChip(
                    selected = indicator == option,
                    onClick = { onSelectIndicator(option) },
                    label = { Text(option.label) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { onAddVolume(0.5f) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("titration_drop_button"),
            ) {
                Icon(Icons.Rounded.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+0.5 mL")
            }
            FilledTonalButton(
                onClick = { onAddVolume(2.0f) },
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Text("+2.0 mL")
            }
            OutlinedButton(
                onClick = onToggleDrip,
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Text(if (isDripping) "Stop" else "Auto Drip")
            }
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset flask")
            }
        }

        Text("Burette Volume Added: ${String.format(Locale.US, "%.1f", metrics.baseAddedMl)} mL", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = metrics.baseAddedMl,
            onValueChange = onVolumeChange,
            valueRange = 0f..60f,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("HCl: ${String.format(Locale.US, "%.2f", metrics.acidMolarity)} M", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = metrics.acidMolarity,
                    onValueChange = onAcidMolarityChange,
                    valueRange = 0.10f..0.50f,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("NaOH: ${String.format(Locale.US, "%.2f", metrics.baseMolarity)} M", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = metrics.baseMolarity,
                    onValueChange = onBaseMolarityChange,
                    valueRange = 0.10f..0.50f,
                )
            }
        }
    }
}

@Composable
fun PendulumControlsCard(
    metrics: PendulumMetrics,
    gravityPreset: GravityPreset,
    running: Boolean,
    onLengthChange: (Float) -> Unit,
    onAngleChange: (Float) -> Unit,
    onSelectGravity: (GravityPreset) -> Unit,
    onToggleRunning: () -> Unit,
    onResetClock: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabMetricPill("Period (T)", "${String.format(Locale.US, "%.2f", metrics.periodSec)} s", Modifier.weight(1f))
            LabMetricPill("Freq (f)", "${String.format(Locale.US, "%.2f", metrics.frequencyHz)} Hz", Modifier.weight(1f))
            LabMetricPill("Cycles", metrics.completedOscillations.toString(), Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GravityPreset.entries.forEach { preset ->
                FilterChip(
                    selected = gravityPreset == preset,
                    onClick = { onSelectGravity(preset) },
                    label = { Text(preset.label) },
                )
            }
        }

        Text("String Length (L): ${String.format(Locale.US, "%.2f", metrics.lengthMeters)} m", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = metrics.lengthMeters,
            onValueChange = onLengthChange,
            valueRange = 0.20f..2.50f,
        )

        Text("Release Angle (θ₀): ${metrics.releaseAngleDeg.toInt()}°", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = metrics.releaseAngleDeg,
            onValueChange = onAngleChange,
            valueRange = 5f..35f,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = onToggleRunning,
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(
                    imageVector = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (running) "Pause Oscillation" else "Resume Oscillation")
            }
            OutlinedButton(
                onClick = onResetClock,
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset t=0")
            }
        }
    }
}

@Composable
fun SensorBenchControlsCard(telemetry: SensorTelemetry) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabMetricPill(
                label = "Gravity |a|",
                value = if (telemetry.hasAccelerometer) "${String.format(Locale.US, "%.2f", telemetry.accelMagnitude)} m/s²" else "N/A",
                modifier = Modifier.weight(1f),
            )
            LabMetricPill(
                label = "Tilt Angle",
                value = if (telemetry.hasAccelerometer) "${String.format(Locale.US, "%.1f", telemetry.tiltDegrees)}°" else "N/A",
                modifier = Modifier.weight(1f),
            )
            LabMetricPill(
                label = "Light",
                value = if (telemetry.hasLightSensor) "${String.format(Locale.US, "%.0f", telemetry.lightLux)} lx" else "N/A",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabMetricPill(
                label = "Magnetic |B|",
                value = if (telemetry.hasMagnetometer) "${String.format(Locale.US, "%.1f", telemetry.magMicroTesla)} μT" else "N/A",
                modifier = Modifier.weight(1f),
            )
            LabMetricPill(
                label = "Accel XYZ",
                value = if (telemetry.hasAccelerometer) {
                    "${String.format(Locale.US, "%.1f", telemetry.accelX)}, ${String.format(Locale.US, "%.1f", telemetry.accelY)}, ${String.format(Locale.US, "%.1f", telemetry.accelZ)}"
                } else {
                    "No sensor"
                },
                modifier = Modifier.weight(1.6f),
            )
        }
        Text(
            text = "Streaming real hardware sensor events from SensorManager. Tilt or move your device (or use virtual sensor controls on an emulator) to observe live peaks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ObservationTableCard(
    observations: List<LabObservation>,
    canSaveToDeck: Boolean,
    onSaveFlashcard: (LabObservation) -> Unit,
    onDeleteRow: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Observation Notebook (${observations.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (observations.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear")
                    }
                }
            }

            if (observations.isEmpty()) {
                Text(
                    text = "Tap \"Record Reading\" above after tuning the controls to build your practical observation table.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                observations.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${row.experiment.title} • ${row.recordedAtLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = row.headline,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = row.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (canSaveToDeck) {
                            IconButton(
                                onClick = { onSaveFlashcard(row) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BookmarkAdd,
                                    contentDescription = "Save observation as flashcard",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteRow(row.id) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Remove observation",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiLabPartnerCard(
    aiState: AiLabState,
    onAskPrompt: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var questionInput by remember { mutableStateOf("") }
    val isBusy = aiState is AiLabState.Loading || aiState is AiLabState.Streaming

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "AI Science Lab Partner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Analyzes your live apparatus settings & recorded observation table",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AssistChip(
                    onClick = { onAskPrompt("Explain what is happening right now on my workbench and verify the formula.") },
                    enabled = !isBusy,
                    label = { Text("Explain Result") },
                )
                AssistChip(
                    onClick = { onAskPrompt("Review my recorded Observation Table for trends, accuracy, or errors.") },
                    enabled = !isBusy,
                    label = { Text("Analyze Readings") },
                )
                AssistChip(
                    onClick = { onAskPrompt("Ask me one tough practical viva-voce question based on this experiment.") },
                    enabled = !isBusy,
                    label = { Text("Viva Question") },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = questionInput,
                    onValueChange = { questionInput = it },
                    placeholder = { Text("Ask about this practical or your readings...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lab_ai_input"),
                )
                Button(
                    onClick = {
                        val clean = questionInput.trim()
                        if (clean.isNotEmpty()) {
                            onAskPrompt(clean)
                            questionInput = ""
                        }
                    },
                    enabled = questionInput.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("lab_ai_send_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask AI Lab Partner")
                }
            }

            AnimatedVisibility(visible = aiState !is AiLabState.Idle) {
                when (aiState) {
                    AiLabState.Idle -> Unit
                    AiLabState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 6.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Analyzing live experiment data…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is AiLabState.Streaming -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = aiState.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    is AiLabState.Success -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = aiState.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    is AiLabState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = aiState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onDismissError) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LabMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
