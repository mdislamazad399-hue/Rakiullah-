package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.unwrapFirestoreThrowable
import com.example.data.model.LiveItemType
import com.example.data.model.LiveSession
import com.example.data.model.StudyDeck
import com.example.data.repository.StudyRepository
import com.example.lab.GravityPreset
import com.example.lab.LabObservation
import com.example.lab.OhmsLawMetrics
import com.example.lab.PendulumMetrics
import com.example.lab.ScienceExperimentType
import com.example.lab.ScienceLabEngine
import com.example.lab.SensorTelemetry
import com.example.lab.TitrationIndicator
import com.example.lab.TitrationMetrics
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface AiLabState {
    data object Idle : AiLabState
    data object Loading : AiLabState
    data class Streaming(val text: String) : AiLabState
    data class Success(val text: String) : AiLabState
    data class Error(val message: String) : AiLabState
}

class SciencePracticalViewModel(
    private val repository: StudyRepository,
    private val currentUserId: String,
    private val deckId: String?,
) : ViewModel() {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are StudyDeck's Live Science Practical Lab Partner. " +
                        "You help students and teachers analyze live Physics, Chemistry, and real sensor readings. " +
                        "Verify formulas (Ohm's Law V=IR, Acid-Base titration M1V1=M2V2 & pH, Pendulum T=2*pi*sqrt(L/g), or sensor vectors), " +
                        "spot experimental trends or errors in the recorded Observation Table, and ask crisp viva-voce check questions when asked. " +
                        "Keep answers structured, accurate, and easy to read on mobile."
                )
            },
        )
    }

    val linkedDeck: StateFlow<StudyDeck?> =
        if (deckId.isNullOrBlank()) {
            MutableStateFlow<StudyDeck?>(null).asStateFlow()
        } else {
            repository.observeDeck(deckId)
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    Log.w(TAG, "Error observing deck in practical room", root)
                    emit(null)
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)
        }

    val liveSession: StateFlow<LiveSession?> =
        if (deckId.isNullOrBlank()) {
            MutableStateFlow<LiveSession?>(null).asStateFlow()
        } else {
            repository.observeLiveSession(deckId)
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    Log.w(TAG, "Error observing live session in practical room", root)
                    emit(null)
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)
        }

    private val _selectedExperiment = MutableStateFlow(ScienceExperimentType.OHMS_LAW)
    val selectedExperiment: StateFlow<ScienceExperimentType> = _selectedExperiment.asStateFlow()

    // --- Experiment 1: Ohm's Law State ---
    private val _voltageV = MutableStateFlow(12.0f)
    val voltageV: StateFlow<Float> = _voltageV.asStateFlow()

    private val _resistanceOhm = MutableStateFlow(40.0f)
    val resistanceOhm: StateFlow<Float> = _resistanceOhm.asStateFlow()

    private val _switchClosed = MutableStateFlow(true)
    val switchClosed: StateFlow<Boolean> = _switchClosed.asStateFlow()

    // --- Experiment 2: pH Titration State ---
    private val _acidMolarity = MutableStateFlow(0.20f)
    val acidMolarity: StateFlow<Float> = _acidMolarity.asStateFlow()

    private val _baseMolarity = MutableStateFlow(0.20f)
    val baseMolarity: StateFlow<Float> = _baseMolarity.asStateFlow()

    private val _baseAddedMl = MutableStateFlow(10.0f)
    val baseAddedMl: StateFlow<Float> = _baseAddedMl.asStateFlow()

    private val _titrationIndicator = MutableStateFlow(TitrationIndicator.PHENOLPHTHALEIN)
    val titrationIndicator: StateFlow<TitrationIndicator> = _titrationIndicator.asStateFlow()

    private val _continuousDrip = MutableStateFlow(false)
    val continuousDrip: StateFlow<Boolean> = _continuousDrip.asStateFlow()

    // --- Experiment 3: Simple Pendulum State ---
    private val _pendulumLengthM = MutableStateFlow(1.20f)
    val pendulumLengthM: StateFlow<Float> = _pendulumLengthM.asStateFlow()

    private val _gravityPreset = MutableStateFlow(GravityPreset.EARTH)
    val gravityPreset: StateFlow<GravityPreset> = _gravityPreset.asStateFlow()

    private val _releaseAngleDeg = MutableStateFlow(22.0f)
    val releaseAngleDeg: StateFlow<Float> = _releaseAngleDeg.asStateFlow()

    private val _pendulumRunning = MutableStateFlow(true)
    val pendulumRunning: StateFlow<Boolean> = _pendulumRunning.asStateFlow()

    private val _pendulumElapsedSec = MutableStateFlow(0f)
    val pendulumElapsedSec: StateFlow<Float> = _pendulumElapsedSec.asStateFlow()

    // --- Observations & AI ---
    private val _observations = MutableStateFlow<List<LabObservation>>(emptyList())
    val observations: StateFlow<List<LabObservation>> = _observations.asStateFlow()

    private val _aiState = MutableStateFlow<AiLabState>(AiLabState.Idle)
    val aiState: StateFlow<AiLabState> = _aiState.asStateFlow()

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun selectExperiment(experiment: ScienceExperimentType) {
        _selectedExperiment.value = experiment
    }

    fun setVoltage(v: Float) {
        _voltageV.value = v.coerceIn(1f, 24f)
    }

    fun setResistance(r: Float) {
        _resistanceOhm.value = r.coerceIn(5f, 200f)
    }

    fun toggleSwitch() {
        _switchClosed.update { !it }
    }

    fun setAcidMolarity(m: Float) {
        _acidMolarity.value = m.coerceIn(0.10f, 0.50f)
    }

    fun setBaseMolarity(m: Float) {
        _baseMolarity.value = m.coerceIn(0.10f, 0.50f)
    }

    fun addTitrationVolume(deltaMl: Float) {
        _baseAddedMl.update { (it + deltaMl).coerceIn(0f, 60f) }
    }

    fun setTitrationVolume(ml: Float) {
        _baseAddedMl.value = ml.coerceIn(0f, 60f)
    }

    fun resetTitration() {
        _continuousDrip.value = false
        _baseAddedMl.value = 0f
    }

    fun setIndicator(indicator: TitrationIndicator) {
        _titrationIndicator.value = indicator
    }

    fun toggleContinuousDrip() {
        _continuousDrip.update { !it }
    }

    fun setPendulumLength(meters: Float) {
        _pendulumLengthM.value = meters.coerceIn(0.20f, 2.50f)
    }

    fun setGravityPreset(preset: GravityPreset) {
        _gravityPreset.value = preset
    }

    fun setReleaseAngle(deg: Float) {
        _releaseAngleDeg.value = deg.coerceIn(5f, 35f)
    }

    fun togglePendulumRunning() {
        _pendulumRunning.update { !it }
    }

    fun resetPendulumClock() {
        _pendulumElapsedSec.value = 0f
    }

    fun advancePendulumClock(deltaSec: Float) {
        _pendulumElapsedSec.update { it + deltaSec }
    }

    fun currentOhmsMetrics(): OhmsLawMetrics =
        ScienceLabEngine.computeOhmsLaw(_voltageV.value, _resistanceOhm.value, _switchClosed.value)

    fun currentTitrationMetrics(): TitrationMetrics =
        ScienceLabEngine.computeTitration(
            acidMolarity = _acidMolarity.value,
            acidVolumeMl = ACID_FLASK_VOLUME_ML,
            baseMolarity = _baseMolarity.value,
            baseAddedMl = _baseAddedMl.value,
            indicator = _titrationIndicator.value,
        )

    fun currentPendulumMetrics(): PendulumMetrics =
        ScienceLabEngine.computePendulum(
            lengthMeters = _pendulumLengthM.value,
            gravityMs2 = _gravityPreset.value.gravityMs2,
            releaseAngleDeg = _releaseAngleDeg.value,
            elapsedSec = _pendulumElapsedSec.value,
        )

    fun recordCurrentObservation(sensorTelemetry: SensorTelemetry) {
        val exp = _selectedExperiment.value
        val timestamp = timeFormat.format(Date())
        val entry = when (exp) {
            ScienceExperimentType.OHMS_LAW -> {
                val m = currentOhmsMetrics()
                LabObservation(
                    experiment = exp,
                    headline = "V=${String.format(Locale.US, "%.1f", m.voltageV)}V, R=${m.resistanceOhm.toInt()}Ω → I=${String.format(Locale.US, "%.3f", m.currentAmps)}A",
                    detail = "Switch: ${if (m.switchClosed) "Closed" else "Open"} • Power=${String.format(Locale.US, "%.2f", m.powerWatts)}W",
                    recordedAtLabel = timestamp,
                )
            }
            ScienceExperimentType.PH_TITRATION -> {
                val m = currentTitrationMetrics()
                LabObservation(
                    experiment = exp,
                    headline = "NaOH=${String.format(Locale.US, "%.1f", m.baseAddedMl)}mL → pH=${String.format(Locale.US, "%.2f", m.pH)}",
                    detail = "${m.solutionLabel} • EqPt=${String.format(Locale.US, "%.1f", m.equivalenceVolumeMl)}mL (${_titrationIndicator.value.label})",
                    recordedAtLabel = timestamp,
                )
            }
            ScienceExperimentType.PENDULUM -> {
                val m = currentPendulumMetrics()
                LabObservation(
                    experiment = exp,
                    headline = "L=${String.format(Locale.US, "%.2f", m.lengthMeters)}m (${_gravityPreset.value.label}) → T=${String.format(Locale.US, "%.2f", m.periodSec)}s",
                    detail = "f=${String.format(Locale.US, "%.2f", m.frequencyHz)}Hz • θ=${String.format(Locale.US, "%.1f", m.currentAngleDeg)}° • Cycles=${m.completedOscillations}",
                    recordedAtLabel = timestamp,
                )
            }
            ScienceExperimentType.SENSOR_BENCH -> {
                val accelPart = if (sensorTelemetry.hasAccelerometer) {
                    "|a|=${String.format(Locale.US, "%.2f", sensorTelemetry.accelMagnitude)}m/s² (tilt ${String.format(Locale.US, "%.1f", sensorTelemetry.tiltDegrees)}°)"
                } else {
                    "Accel N/A"
                }
                val extraParts = buildList {
                    if (sensorTelemetry.hasLightSensor) {
                        add("${String.format(Locale.US, "%.0f", sensorTelemetry.lightLux)} lux")
                    }
                    if (sensorTelemetry.hasMagnetometer) {
                        add("|B|=${String.format(Locale.US, "%.1f", sensorTelemetry.magMicroTesla)}μT")
                    }
                }.joinToString(" • ").ifBlank { "Hardware sensors polled" }
                LabObservation(
                    experiment = exp,
                    headline = accelPart,
                    detail = extraParts,
                    recordedAtLabel = timestamp,
                )
            }
        }
        _observations.update { listOf(entry) + it.take(24) }
        if (_isBroadcasting.value) {
            broadcastCurrentState(sensorTelemetry, forceActive = true)
        }
    }

    fun removeObservation(id: String) {
        _observations.update { list -> list.filterNot { it.id == id } }
    }

    fun clearObservations() {
        _observations.value = emptyList()
    }

    fun toggleLiveBroadcast(sensorTelemetry: SensorTelemetry) {
        val deck = linkedDeck.value
        if (deck == null) {
            viewModelScope.launch {
                _events.emit("Open this Practical Room from a deck you teach to broadcast live to students.")
            }
            return
        }
        if (deck.teacherId != currentUserId) {
            viewModelScope.launch {
                _events.emit("Only the deck instructor can broadcast live.")
            }
            return
        }
        val next = !_isBroadcasting.value
        _isBroadcasting.value = next
        broadcastCurrentState(sensorTelemetry, forceActive = next)
    }

    fun broadcastCurrentState(sensorTelemetry: SensorTelemetry, forceActive: Boolean = _isBroadcasting.value) {
        val deck = linkedDeck.value ?: return
        if (deck.teacherId != currentUserId) return

        val prompt = if (!forceActive) {
            ""
        } else {
            buildLiveSummary(sensorTelemetry).take(290)
        }
        viewModelScope.launch {
            repository.updateLiveSession(
                deck = deck,
                active = forceActive,
                itemType = LiveItemType.NONE,
                itemIndex = _selectedExperiment.value.ordinal,
                activePrompt = prompt,
            ).onFailure { e ->
                _isBroadcasting.value = false
                _events.emit(e.message ?: "Failed to sync live practical.")
            }
        }
    }

    fun saveObservationAsFlashcard(observation: LabObservation) {
        val deck = linkedDeck.value
        if (deck == null || deck.teacherId != currentUserId) {
            viewModelScope.launch {
                _events.emit("Open from one of your created decks to save findings as a flashcard.")
            }
            return
        }
        viewModelScope.launch {
            val front = "${observation.experiment.title}: ${observation.headline}".take(300)
            val back = "${observation.detail}\nFormula: ${observation.experiment.formulaHint}".take(500)
            repository.addFlashcard(
                deckId = deck.id,
                front = front,
                back = back,
                hint = "Recorded in Live Science Practical Room",
                published = deck.published,
                orderIndex = deck.flashcardCount.toInt(),
            ).onSuccess {
                _events.emit("Saved lab observation as a flashcard in \"${deck.title}\"!")
            }.onFailure { e ->
                _events.emit(e.message ?: "Failed to save flashcard.")
            }
        }
    }

    fun askAiLabPartner(
        question: String,
        sensorTelemetry: SensorTelemetry,
    ) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty() || _aiState.value is AiLabState.Loading || _aiState.value is AiLabState.Streaming) {
            return
        }

        val snapshotContext = buildAiLabPrompt(cleanQuestion, sensorTelemetry)
        _aiState.value = AiLabState.Loading

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    model.generateContentStream(snapshotContext).collect { chunk ->
                        val part = chunk.text.orEmpty()
                        if (part.isNotEmpty()) {
                            accumulated.append(part)
                            _aiState.value = AiLabState.Streaming(accumulated.toString())
                        }
                    }
                }
                val finalText = accumulated.toString().trim()
                if (finalText.isEmpty()) {
                    _aiState.value = AiLabState.Error("AI Lab Partner returned an empty response. Please try again.")
                } else {
                    _aiState.value = AiLabState.Success(finalText)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI Lab Partner timed out", e)
                _aiState.value = AiLabState.Error("AI analysis timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Lab Partner failed", e)
                _aiState.value = AiLabState.Error(e.localizedMessage ?: "AI Lab Partner could not respond.")
            }
        }
    }

    fun dismissAiError() {
        if (_aiState.value is AiLabState.Error) {
            _aiState.value = AiLabState.Idle
        }
    }

    private fun buildLiveSummary(sensorTelemetry: SensorTelemetry): String {
        val exp = _selectedExperiment.value
        return when (exp) {
            ScienceExperimentType.OHMS_LAW -> {
                val m = currentOhmsMetrics()
                "[Science Lab: Ohm's Law] V=${String.format(Locale.US, "%.1f", m.voltageV)}V, R=${m.resistanceOhm.toInt()}Ω → I=${String.format(Locale.US, "%.3f", m.currentAmps)}A, P=${String.format(Locale.US, "%.2f", m.powerWatts)}W"
            }
            ScienceExperimentType.PH_TITRATION -> {
                val m = currentTitrationMetrics()
                "[Science Lab: Titration] NaOH=${String.format(Locale.US, "%.1f", m.baseAddedMl)}mL, pH=${String.format(Locale.US, "%.2f", m.pH)} (${m.solutionLabel})"
            }
            ScienceExperimentType.PENDULUM -> {
                val m = currentPendulumMetrics()
                "[Science Lab: Pendulum] L=${String.format(Locale.US, "%.2f", m.lengthMeters)}m, g=${m.gravityMs2}m/s² → T=${String.format(Locale.US, "%.2f", m.periodSec)}s"
            }
            ScienceExperimentType.SENSOR_BENCH -> {
                "[Science Lab: Live Sensors] |a|=${String.format(Locale.US, "%.2f", sensorTelemetry.accelMagnitude)}m/s², Light=${String.format(Locale.US, "%.0f", sensorTelemetry.lightLux)}lux"
            }
        }
    }

    private fun buildAiLabPrompt(question: String, sensorTelemetry: SensorTelemetry): String {
        val currentState = buildLiveSummary(sensorTelemetry)
        val recentRows = _observations.value.take(8).joinToString("\n") { row ->
            "- [${row.recordedAtLabel}] ${row.experiment.title}: ${row.headline} (${row.detail})"
        }.ifBlank { "No saved observation rows yet." }

        return """
            Active Science Experiment: ${_selectedExperiment.value.title} (${_selectedExperiment.value.discipline})
            Governing Formula: ${_selectedExperiment.value.formulaHint}
            Live Workbench Telemetry: $currentState

            Recorded Observation Table:
            $recentRows

            User Request: $question
        """.trimIndent()
    }

    companion object {
        const val ACID_FLASK_VOLUME_ML = 25.0f
        private const val TAG = "SciencePracticalVM"
    }
}
