package com.atharok.btremote.ui.views.mouse

import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.btremote.presentation.viewmodel.GyroscopeSensorViewModel
import org.koin.androidx.compose.koinViewModel

private const val OFFSET = 10
private const val MIN_MOUSE_DELTA = 0.05f

@Composable
fun MouseGyroscope(
    mouseSpeed: Float,
    onMousePositionChange: (Float, Float) -> Unit,
    gyroscopeSensorViewModel: GyroscopeSensorViewModel = koinViewModel()
) {
    val currentMouseSpeed = rememberUpdatedState(mouseSpeed)
    val currentMousePositionCallback = rememberUpdatedState(onMousePositionChange)

    DisposableEffect(Unit) {
        gyroscopeSensorViewModel.startListening()
        onDispose {
            gyroscopeSensorViewModel.stopListening()
        }
    }

    val positions: Triple<Float, Float, Float> = gyroscopeSensorViewModel.positions.collectAsStateWithLifecycle().value

    LaunchedEffect(positions) {
        val (x, y, z) = positions
        val (dx, dy) = calculateMouseDirection(
            x, y, z,
            gyroscopeSensorViewModel.getDisplayRotation(),
            currentMouseSpeed.value
        )
        if (kotlin.math.abs(dx) >= MIN_MOUSE_DELTA || kotlin.math.abs(dy) >= MIN_MOUSE_DELTA) {
            currentMousePositionCallback.value(dx, dy)
        }
    }
}

private fun calculateMouseDirection(
    x: Float, y: Float, z: Float,
    rotation: Int,
    speed: Float
): Pair<Float, Float> {
    val factor: Float = OFFSET * speed * -1
    return when (rotation) {
        Surface.ROTATION_0 -> z * factor to x * factor
        Surface.ROTATION_180 -> z * factor to x * factor * -1
        Surface.ROTATION_270 -> z * factor to y * factor
        Surface.ROTATION_90 -> z * factor to y * factor * -1
        else -> 0f to 0f
    }
}