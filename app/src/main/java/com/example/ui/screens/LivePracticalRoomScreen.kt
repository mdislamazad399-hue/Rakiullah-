package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.lab.ScienceExperimentType
import com.example.lab.rememberDeviceSensorTelemetry
import com.example.ui.components.AiLabPartnerCard
import com.example.ui.components.CameraPreview
import com.example.ui.components.ObservationTableCard
import com.example.ui.components.OhmsLawCircuitCanvas
import com.example.ui.components.OhmsLawControlsCard
import com.example.ui.components.PendulumControlsCard
import com.example.ui.components.PendulumLabCanvas
import com.example.ui.components.SensorBenchControlsCard
import com.example.ui.components.SensorOscilloscopeCanvas
import com.example.ui.components.TitrationControlsCard
import com.example.ui.components.TitrationLabCanvas
import com.example.ui.viewmodel.SciencePracticalViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePracticalRoomScreen(
    viewModel: SciencePracticalViewModel,
    currentUserId: String,
    onBack: () -> Unit,
) {
    val selectedExperiment by viewModel.selectedExperiment.collectAsStateWithLifecycle()
    val linkedDeck by viewModel.linkedDeck.collectAsStateWithLifecycle()
    val liveSession by viewModel.liveSession.collectAsStateWithLifecycle()
    val observations by viewModel.observations.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val isBroadcasting by viewModel.isBroadcasting.collectAsStateWithLifecycle()

    // Experiment parameters
    val voltageV by viewModel.voltageV.collectAsStateWithLifecycle()
    val resistanceOhm by viewModel.resistanceOhm.collectAsStateWithLifecycle()
    val switchClosed by viewModel.switchClosed.collectAsStateWithLifecycle()

    val acidMolarity by viewModel.acidMolarity.collectAsStateWithLifecycle()
    val baseMolarity by viewModel.baseMolarity.collectAsStateWithLifecycle()
    val baseAddedMl by viewModel.baseAddedMl.collectAsStateWithLifecycle()
    val titrationIndicator by viewModel.titrationIndicator.collectAsStateWithLifecycle()
    val continuousDrip by viewModel.continuousDrip.collectAsStateWithLifecycle()

    val pendulumLengthM by viewModel.pendulumLengthM.collectAsStateWithLifecycle()
    val gravityPreset by viewModel.gravityPreset.collectAsStateWithLifecycle()
    val releaseAngleDeg by viewModel.releaseAngleDeg.collectAsStateWithLifecycle()
    val pendulumRunning by viewModel.pendulumRunning.collectAsStateWithLifecycle()
    val pendulumElapsedSec by viewModel.pendulumElapsedSec.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showLabCameraPip by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showLabCameraPip = true
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Camera permission is needed to preview your physical lab bench.")
            }
        }
    }

    val sensorTelemetry = rememberDeviceSensorTelemetry(
        enabled = selectedExperiment == ScienceExperimentType.SENSOR_BENCH
    )

    // Real-time 60fps animation loop for Simple Pendulum kinematics
    LaunchedEffect(selectedExperiment, pendulumRunning) {
        if (selectedExperiment == ScienceExperimentType.PENDULUM && pendulumRunning) {
            var lastNanos = 0L
            while (true) {
                withFrameNanos { frameNanos ->
                    if (lastNanos != 0L) {
                        val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        viewModel.advancePendulumClock(dt)
                    }
                    lastNanos = frameNanos
                }
            }
        }
    }

    // Continuous burette drip loop for Titration
    LaunchedEffect(selectedExperiment, continuousDrip) {
        if (selectedExperiment == ScienceExperimentType.PH_TITRATION && continuousDrip) {
            while (true) {
                delay(380L)
                viewModel.addTitrationVolume(0.5f)
                if (viewModel.baseAddedMl.value >= 60f) {
                    viewModel.toggleContinuousDrip()
                    break
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val isDeckTeacher = linkedDeck?.teacherId == currentUserId

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = linkedDeck?.title?.let { "$it • Lab" }
                                ?: stringResource(R.string.science_lab_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = selectedExperiment.discipline,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isBroadcasting) {
                                viewModel.broadcastCurrentState(sensorTelemetry, forceActive = false)
                            }
                            onBack()
                        },
                        modifier = Modifier.testTag("practical_room_back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = {
                            if (showLabCameraPip) {
                                showLabCameraPip = false
                            } else {
                                val hasCam = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCam) {
                                    showLabCameraPip = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        },
                        modifier = Modifier.testTag("toggle_lab_camera_pip"),
                    ) {
                        Icon(
                            imageVector = if (showLabCameraPip) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                            contentDescription = stringResource(R.string.science_lab_camera_pip),
                        )
                    }
                    if (isDeckTeacher) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { viewModel.toggleLiveBroadcast(sensorTelemetry) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBroadcasting) Color(0xFFD93829) else MaterialTheme.colorScheme.primary,
                            ),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .padding(end = 8.dp)
                                .testTag("broadcast_practical_button"),
                        ) {
                            Icon(
                                imageVector = if (isBroadcasting) Icons.Rounded.StopCircle else Icons.Rounded.Cast,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isBroadcasting) "End Live" else "Go Live", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        val ohmsMetrics = remember(voltageV, resistanceOhm, switchClosed) {
            viewModel.currentOhmsMetrics()
        }
        val titrationMetrics = remember(acidMolarity, baseMolarity, baseAddedMl, titrationIndicator) {
            viewModel.currentTitrationMetrics()
        }
        val pendulumMetrics = remember(pendulumLengthM, gravityPreset, releaseAngleDeg, pendulumElapsedSec) {
            viewModel.currentPendulumMetrics()
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val isWide = maxWidth >= 860.dp

            val workbenchColumn: @Composable (Modifier) -> Unit = { colModifier ->
                Column(
                    modifier = colModifier,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Student live-sync banner when watching a teacher's active practical
                    val activePrompt = liveSession?.takeIf { it.active && it.activePrompt.isNotBlank() }?.activePrompt
                    if (activePrompt != null && !isDeckTeacher) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.Cast, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Text(
                                    text = "LIVE INSTRUCTOR FEED: $activePrompt",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }

                    // Experiment Selector Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScienceExperimentType.entries.forEach { exp ->
                            FilterChip(
                                selected = selectedExperiment == exp,
                                onClick = { viewModel.selectExperiment(exp) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(exp.title) },
                                modifier = Modifier.testTag("exp_chip_${exp.name}"),
                            )
                        }
                    }

                    // Interactive Simulation Workbench Canvas + Optional PiP Lab Camera
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(270.dp),
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (selectedExperiment) {
                                ScienceExperimentType.OHMS_LAW ->
                                    OhmsLawCircuitCanvas(metrics = ohmsMetrics)
                                ScienceExperimentType.PH_TITRATION ->
                                    TitrationLabCanvas(
                                        metrics = titrationMetrics,
                                        indicator = titrationIndicator,
                                        isDripping = continuousDrip,
                                    )
                                ScienceExperimentType.PENDULUM ->
                                    PendulumLabCanvas(metrics = pendulumMetrics)
                                ScienceExperimentType.SENSOR_BENCH ->
                                    SensorOscilloscopeCanvas(telemetry = sensorTelemetry)
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp),
                            ) {
                                Text(
                                    text = selectedExperiment.formulaHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE2E8F0),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }

                            this@Column.AnimatedVisibility(
                                visible = showLabCameraPip,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp),
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(width = 112.dp, height = 148.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f)),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    CameraPreview(
                                        cameraEnabled = showLabCameraPip,
                                        useFrontCamera = false,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    // Parameter Controls + Record Action
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            when (selectedExperiment) {
                                ScienceExperimentType.OHMS_LAW ->
                                    OhmsLawControlsCard(
                                        metrics = ohmsMetrics,
                                        onVoltageChange = viewModel::setVoltage,
                                        onResistanceChange = viewModel::setResistance,
                                        onToggleSwitch = viewModel::toggleSwitch,
                                    )
                                ScienceExperimentType.PH_TITRATION ->
                                    TitrationControlsCard(
                                        metrics = titrationMetrics,
                                        indicator = titrationIndicator,
                                        isDripping = continuousDrip,
                                        onAddVolume = viewModel::addTitrationVolume,
                                        onVolumeChange = viewModel::setTitrationVolume,
                                        onAcidMolarityChange = viewModel::setAcidMolarity,
                                        onBaseMolarityChange = viewModel::setBaseMolarity,
                                        onSelectIndicator = viewModel::setIndicator,
                                        onToggleDrip = viewModel::toggleContinuousDrip,
                                        onReset = viewModel::resetTitration,
                                    )
                                ScienceExperimentType.PENDULUM ->
                                    PendulumControlsCard(
                                        metrics = pendulumMetrics,
                                        gravityPreset = gravityPreset,
                                        running = pendulumRunning,
                                        onLengthChange = viewModel::setPendulumLength,
                                        onAngleChange = viewModel::setReleaseAngle,
                                        onSelectGravity = viewModel::setGravityPreset,
                                        onToggleRunning = viewModel::togglePendulumRunning,
                                        onResetClock = viewModel::resetPendulumClock,
                                    )
                                ScienceExperimentType.SENSOR_BENCH ->
                                    SensorBenchControlsCard(telemetry = sensorTelemetry)
                            }

                            Button(
                                onClick = { viewModel.recordCurrentObservation(sensorTelemetry) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("record_observation_button"),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.science_lab_record))
                            }
                        }
                    }
                }
            }

            val analysisColumn: @Composable (Modifier) -> Unit = { colModifier ->
                Column(
                    modifier = colModifier,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AiLabPartnerCard(
                        aiState = aiState,
                        onAskPrompt = { prompt -> viewModel.askAiLabPartner(prompt, sensorTelemetry) },
                        onDismissError = viewModel::dismissAiError,
                    )

                    ObservationTableCard(
                        observations = observations,
                        canSaveToDeck = isDeckTeacher,
                        onSaveFlashcard = viewModel::saveObservationAsFlashcard,
                        onDeleteRow = viewModel::removeObservation,
                        onClearAll = viewModel::clearObservations,
                    )
                }
            }

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    workbenchColumn(
                        Modifier
                            .weight(1.15f)
                            .verticalScroll(rememberScrollState()),
                    )
                    analysisColumn(
                        Modifier
                            .weight(0.85f)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        workbenchColumn(Modifier.fillMaxWidth())
                        analysisColumn(Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
