package com.atharok.btremote.data.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GyroscopeSensor(
    private val sensorManager: SensorManager,
    private val displayManager: DisplayManager
) : SensorEventListener {

    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var isListening = false

    private val _gyroscopePositionsState =
        MutableStateFlow(Triple(0f, 0f, 0f))
    val gyroscopePositionsState: StateFlow<Triple<Float, Float, Float>> =
        _gyroscopePositionsState.asStateFlow()

    fun startListening() {
        if (isListening) return
        gyroscope?.let { sensor ->
            isListening = try {
                sensorManager.registerListener(this, sensor, SENSOR_DELAY_US)
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Unable to register gyroscope listener", exception)
                false
            }
        }
    }

    fun stopListening() {
        if (!isListening) return
        try {
            sensorManager.unregisterListener(this)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to unregister gyroscope listener", exception)
        } finally {
            isListening = false
        }
    }

    // Resolve the display at call time so rotations/configuration changes are not stale.
    fun getDisplayRotation(): Int = try {
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
    } catch (exception: RuntimeException) {
        Log.w(TAG, "Unable to read current display rotation", exception)
        Surface.ROTATION_0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE && event.values.size >= 3) {
            _gyroscopePositionsState.value =
                Triple(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val TAG = "GyroscopeSensor"

        // Preserve existing 100 Hz sampling; tune only after device profiling.
        const val SENSOR_DELAY_US = 10_000
    }
}
