package com.atharok.btremote.data.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GyroscopeSensor(
    private val sensorManager: SensorManager,
    displayManager: DisplayManager
): SensorEventListener {

    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val display: Display? = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    private var isListening = false

    private val _gyroscopePositionsState: MutableStateFlow<Triple<Float, Float, Float>> = MutableStateFlow(Triple(0f, 0f, 0f))
    val gyroscopePositionsState: StateFlow<Triple<Float, Float, Float>> = _gyroscopePositionsState.asStateFlow()

    fun startListening() {
        if (isListening) return
        gyroscope?.let { sensor ->
            isListening = sensorManager.registerListener(this, sensor, SENSOR_DELAY_US)
        }
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    fun getDisplayRotation(): Int = display?.rotation ?: Surface.ROTATION_0

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            _gyroscopePositionsState.value = Triple(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        // Preserve the existing 100 Hz sampling rate; tune only after device profiling.
        const val SENSOR_DELAY_US = 10_000
    }
}