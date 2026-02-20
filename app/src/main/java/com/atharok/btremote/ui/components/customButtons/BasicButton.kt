package com.atharok.btremote.ui.components.customButtons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import com.atharok.btremote.ui.components.DefaultElevatedCard
import com.atharok.btremote.ui.theme.surfaceElevationMedium
import com.atharok.btremote.ui.theme.surfaceElevationShadow

@Composable
private fun StatefulCustomButton(
    touchDown: () -> Unit,
    touchUp: () -> Unit,
    hapticFeedbackEnabled: Boolean = true,
    content: @Composable (interactionSource: MutableInteractionSource, scale: Float) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
        label = "ButtonScale"
    )

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    isPressed = true
                    if (hapticFeedbackEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    touchDown()
                }
                is PressInteraction.Release -> {
                    isPressed = false
                    touchUp()
                }
                is PressInteraction.Cancel -> {
                    isPressed = false
                    touchUp()
                }
            }
        }
    }

    content(interactionSource, scale)
}

@Composable
private fun CustomButton(
    touchDown: () -> Unit,
    touchUp: () -> Unit,
    hapticFeedbackEnabled: Boolean = true,
    shape: Shape,
    content: @Composable () -> Unit
) {
    StatefulCustomButton(
        touchDown = touchDown,
        touchUp = touchUp,
        hapticFeedbackEnabled = hapticFeedbackEnabled
    ) { interactionSource, scale ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(shape)
                .clipToBounds()
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

// ---- Surface Button ----

@Composable
fun SurfaceButton(
    touchDown: () -> Unit,
    touchUp: () -> Unit,
    modifier: Modifier = Modifier,
    hapticFeedbackEnabled: Boolean = true,
    shape: Shape = RectangleShape,
    elevation: Dp = surfaceElevationMedium(),
    shadowElevation: Dp = surfaceElevationShadow(),
    content: @Composable () -> Unit
) {
    DefaultElevatedCard(
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        shadowElevation = shadowElevation
    ) {
        CustomButton(
            touchDown = touchDown,
            touchUp = touchUp,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            shape = shape,
            content = content
        )
    }
}

// ---- Raw Button ----

@Composable
fun RawButton(
    touchDown: () -> Unit,
    touchUp: () -> Unit,
    modifier: Modifier = Modifier,
    hapticFeedbackEnabled: Boolean = true,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        CustomButton(
            touchDown = touchDown,
            touchUp = touchUp,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            shape = shape,
            content = content
        )
    }
}
