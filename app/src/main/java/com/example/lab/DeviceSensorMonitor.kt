package com.example.lab

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt

private const val MAX_WAVEFORM_SAMPLES = 48

/**
 * Observes real Android hardware sensors via SensorManager (Accelerometer, Ambient Light, Magnetometer)
 * only while active. Reports exact hardware availability honestly when a sensor is absent.
 */
@Composable
fun rememberDeviceSensorTelemetry(enabled: Boolean): SensorTelemetry {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    var telemetry by remember(sensorManager) {
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val light = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val mag = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mutableStateOf(
            SensorTelemetry(
                hasAccelerometer = accel != null,
                hasLightSensor = light != null,
                hasMagnetometer = mag != null,
            )
        )
    }

    DisposableEffect(sensorManager, enabled) {
        if (!enabled || sensorManager == null) {
            return@DisposableEffect onDispose {}
        }

        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var lastWaveformMs = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        val ax = event.values.getOrNull(0) ?: 0f
                        val ay = event.values.getOrNull(1) ?: 0f
                        val az = event.values.getOrNull(2) ?: 0f
                        val mag = sqrt(ax * ax + ay * ay + az * az)
                        val tilt = ScienceLabEngine.computeTiltDegrees(ax, ay, az)
                        val now = System.currentTimeMillis()
                        val updatedHistory = if (now - lastWaveformMs >= 70L) {
                            lastWaveformMs = now
                            (telemetry.waveformHistory + mag).takeLast(MAX_WAVEFORM_SAMPLES)
                        } else {
                            telemetry.waveformHistory
                        }
                        telemetry = telemetry.copy(
                            hasAccelerometer = true,
                            accelX = ax,
                            accelY = ay,
                            accelZ = az,
                            accelMagnitude = mag,
                            tiltDegrees = tilt,
                            waveformHistory = updatedHistory,
                        )
                    }
                    Sensor.TYPE_LIGHT -> {
                        val lux = event.values.getOrNull(0) ?: 0f
                        telemetry = telemetry.copy(
                            hasLightSensor = true,
                            lightLux = lux,
                        )
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val mx = event.values.getOrNull(0) ?: 0f
                        val my = event.values.getOrNull(1) ?: 0f
                        val mz = event.values.getOrNull(2) ?: 0f
                        val magStrength = sqrt(mx * mx + my * my + mz * mz)
                        telemetry = telemetry.copy(
                            hasMagnetometer = true,
                            magX = mx,
                            magY = my,
                            magZ = mz,
                            magMicroTesla = magStrength,
                        )
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        accelSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        lightSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        magSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return telemetry
}
