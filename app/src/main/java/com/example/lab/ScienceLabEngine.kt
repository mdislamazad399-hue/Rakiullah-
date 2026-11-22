package com.example.lab

import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class ScienceExperimentType(
    val title: String,
    val discipline: String,
    val formulaHint: String,
) {
    OHMS_LAW(
        title = "Ohm's Law",
        discipline = "Physics • Circuits",
        formulaHint = "V = I × R  |  P = V × I",
    ),
    PH_TITRATION(
        title = "pH Titration",
        discipline = "Chemistry • Acid–Base",
        formulaHint = "M₁V₁ = M₂V₂  |  pH = -log₁₀[H⁺]",
    ),
    PENDULUM(
        title = "Pendulum",
        discipline = "Physics • Gravity",
        formulaHint = "T = 2π√(L / g)",
    ),
    SENSOR_BENCH(
        title = "Live Sensors",
        discipline = "Hardware • Telemetry",
        formulaHint = "|a| = √(ax² + ay² + az²)  |  |B| = √(Bx² + By² + Bz²)",
    ),
}

enum class TitrationIndicator(
    val label: String,
    val rangeNote: String,
) {
    PHENOLPHTHALEIN("Phenolphthalein", "Clear < pH 8.2 → Magenta > 10.0"),
    BROMOTHYMOL_BLUE("Bromothymol Blue", "Yellow < pH 6.0 → Green 7 → Blue > 7.6"),
    UNIVERSAL("Universal", "Red (Acid) → Green (7) → Violet (Base)"),
}

enum class GravityPreset(
    val label: String,
    val gravityMs2: Float,
) {
    EARTH("Earth (9.81)", 9.81f),
    MOON("Moon (1.62)", 1.62f),
    MARS("Mars (3.71)", 3.71f),
}

data class OhmsLawMetrics(
    val voltageV: Float,
    val resistanceOhm: Float,
    val switchClosed: Boolean,
    val currentAmps: Float,
    val powerWatts: Float,
    val heatPerMinuteJoules: Float,
    val bulbBrightness: Float,
)

data class TitrationMetrics(
    val acidMolarity: Float,
    val acidVolumeMl: Float,
    val baseMolarity: Float,
    val baseAddedMl: Float,
    val equivalenceVolumeMl: Float,
    val pH: Float,
    val solutionLabel: String,
    val endpointReached: Boolean,
    val liquidColorArgb: Long,
)

data class PendulumMetrics(
    val lengthMeters: Float,
    val gravityMs2: Float,
    val releaseAngleDeg: Float,
    val periodSec: Float,
    val frequencyHz: Float,
    val currentAngleDeg: Float,
    val angularVelocityRadPerSec: Float,
    val potentialEnergyJ: Float,
    val kineticEnergyJ: Float,
    val completedOscillations: Int,
)

data class LabObservation(
    val id: String = UUID.randomUUID().toString(),
    val experiment: ScienceExperimentType,
    val headline: String,
    val detail: String,
    val recordedAtLabel: String,
)

data class SensorTelemetry(
    val hasAccelerometer: Boolean = false,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val accelMagnitude: Float = 0f,
    val tiltDegrees: Float = 0f,
    val hasLightSensor: Boolean = false,
    val lightLux: Float = 0f,
    val hasMagnetometer: Boolean = false,
    val magX: Float = 0f,
    val magY: Float = 0f,
    val magZ: Float = 0f,
    val magMicroTesla: Float = 0f,
    val waveformHistory: List<Float> = emptyList(),
)

object ScienceLabEngine {

    fun computeOhmsLaw(
        voltageV: Float,
        resistanceOhm: Float,
        switchClosed: Boolean,
    ): OhmsLawMetrics {
        val safeV = voltageV.coerceIn(0f, 24f)
        val safeR = resistanceOhm.coerceIn(1f, 500f)
        val current = if (switchClosed) safeV / safeR else 0f
        val power = if (switchClosed) safeV * current else 0f
        val heat60s = power * 60f
        // Normalize brightness around 5W nominal max for intuitive visual glow
        val brightness = if (switchClosed) (power / 4.5f).coerceIn(0.06f, 1f) else 0f
        return OhmsLawMetrics(
            voltageV = safeV,
            resistanceOhm = safeR,
            switchClosed = switchClosed,
            currentAmps = current,
            powerWatts = power,
            heatPerMinuteJoules = heat60s,
            bulbBrightness = brightness,
        )
    }

    fun computeTitration(
        acidMolarity: Float,
        acidVolumeMl: Float,
        baseMolarity: Float,
        baseAddedMl: Float,
        indicator: TitrationIndicator,
    ): TitrationMetrics {
        val ma = acidMolarity.coerceIn(0.05f, 1.0f).toDouble()
        val va = acidVolumeMl.coerceIn(5f, 100f).toDouble()
        val mb = baseMolarity.coerceIn(0.05f, 1.0f).toDouble()
        val vb = baseAddedMl.coerceIn(0f, 60f).toDouble()

        val eqVol = (ma * va) / mb
        val molesAcid = ma * va / 1000.0
        val molesBase = mb * vb / 1000.0
        val totalVolL = (va + vb) / 1000.0

        val diff = molesAcid - molesBase
        val ph = when {
            abs(diff) < 1e-7 -> 7.0
            diff > 0 -> {
                val hConc = diff / totalVolL
                (-log10(hConc.coerceAtLeast(1e-7))).coerceIn(0.0, 7.0)
            }
            else -> {
                val ohConc = (-diff) / totalVolL
                val pOH = -log10(ohConc.coerceAtLeast(1e-7))
                (14.0 - pOH).coerceIn(7.0, 14.0)
            }
        }.toFloat()

        val stateLabel = when {
            ph < 6.4f -> "Acidic (excess H⁺)"
            ph <= 7.6f -> "Neutral Equivalence Point"
            else -> "Basic (excess OH⁻)"
        }

        val colorArgb = resolveIndicatorColor(ph, indicator)
        return TitrationMetrics(
            acidMolarity = ma.toFloat(),
            acidVolumeMl = va.toFloat(),
            baseMolarity = mb.toFloat(),
            baseAddedMl = vb.toFloat(),
            equivalenceVolumeMl = eqVol.toFloat(),
            pH = ph,
            solutionLabel = stateLabel,
            endpointReached = ph >= 7.0f,
            liquidColorArgb = colorArgb,
        )
    }

    private fun resolveIndicatorColor(pH: Float, indicator: TitrationIndicator): Long =
        when (indicator) {
            TitrationIndicator.PHENOLPHTHALEIN -> when {
                pH < 8.2f -> 0x44CFE8F7L // Clear aqueous solution
                pH < 9.8f -> 0xB8F472B6L // Pale rose endpoint
                else -> 0xD8DB2777L // Vivid magenta pink
            }
            TitrationIndicator.BROMOTHYMOL_BLUE -> when {
                pH < 6.0f -> 0xCCFACC15L // Yellow in acid
                pH <= 7.6f -> 0xCC22C55EL // Green near neutral
                else -> 0xCC2563EBL // Deep blue in base
            }
            TitrationIndicator.UNIVERSAL -> when {
                pH < 3.0f -> 0xCCEF4444L
                pH < 5.5f -> 0xCCF97316L
                pH < 6.6f -> 0xCCEAB308L
                pH <= 7.4f -> 0xCC22C55EL
                pH < 9.5f -> 0xCC06B6D4L
                pH < 11.5f -> 0xCC3B82F6L
                else -> 0xCC7C3AEDL
            }
        }

    fun computePendulum(
        lengthMeters: Float,
        gravityMs2: Float,
        releaseAngleDeg: Float,
        elapsedSec: Float,
    ): PendulumMetrics {
        val safeL = lengthMeters.coerceIn(0.2f, 3.0f)
        val safeG = gravityMs2.coerceIn(0.5f, 30f)
        val safeTheta0Deg = releaseAngleDeg.coerceIn(2f, 45f)

        val omega = sqrt(safeG / safeL)
        val period = (2.0 * PI / omega).toFloat()
        val freq = 1f / period

        val theta0Rad = (safeTheta0Deg * PI / 180.0)
        val currentRad = theta0Rad * cos(omega.toDouble() * elapsedSec.toDouble())
        val currentDeg = (currentRad * 180.0 / PI).toFloat()
        val angularVel = (-theta0Rad * omega * sin(omega.toDouble() * elapsedSec.toDouble())).toFloat()

        // Energy for 1.0 kg bob: E_total = m*g*L*(1 - cos(theta0))
        val maxHeight = safeL * (1.0 - cos(theta0Rad)).toFloat()
        val currentHeight = safeL * (1.0 - cos(currentRad)).toFloat()
        val pe = (1.0f * safeG * currentHeight).coerceAtLeast(0f)
        val totalE = (1.0f * safeG * maxHeight).coerceAtLeast(pe)
        val ke = (totalE - pe).coerceAtLeast(0f)
        val oscillations = (elapsedSec / period).toInt().coerceAtLeast(0)

        return PendulumMetrics(
            lengthMeters = safeL,
            gravityMs2 = safeG,
            releaseAngleDeg = safeTheta0Deg,
            periodSec = period,
            frequencyHz = freq,
            currentAngleDeg = currentDeg,
            angularVelocityRadPerSec = angularVel,
            potentialEnergyJ = pe,
            kineticEnergyJ = ke,
            completedOscillations = oscillations,
        )
    }

    fun computeTiltDegrees(ax: Float, ay: Float, az: Float): Float {
        val mag = sqrt(ax * ax + ay * ay + az * az)
        if (mag < 0.01f) return 0f
        val clampedCos = (az / mag).coerceIn(-1f, 1f)
        return (kotlin.math.acos(clampedCos) * 180.0 / PI).toFloat()
    }
}
