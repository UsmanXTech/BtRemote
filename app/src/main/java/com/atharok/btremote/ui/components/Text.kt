package com.atharok.btremote.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.atharok.btremote.ui.theme.Typography

// ---- Adaptive ----

@Composable
fun AdaptiveText(
    text: String,
    @FloatRange(from = 0.0, to = 1.0) percent: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null
) {
    require(percent in 0f..1f) { "Percentage must be between 0 and 1" }

    var containerSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            containerSize = coordinates.size.toSize()
        },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = color,
            style = TextStyle(
                fontSize = with(LocalDensity.current) {
                    (containerSize.width * percent).coerceAtMost(containerSize.height * percent).toSp()
                }
            ),
            fontWeight = fontWeight,
            textAlign = textAlign,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }
}

// ---- Large ----

@Composable
fun TextLarge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        style = Typography.titleLarge,
        fontWeight = FontWeight.Bold,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        textAlign = textAlign
    )
}

// ---- Medium ----

@Composable
fun TextMedium(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = Typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines
    )
}

// ---- Normal ----

@Composable
fun TextNormal(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    style: TextStyle = Typography.titleSmall,
    fontWeight: FontWeight? = null,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        style = style,
        fontWeight = fontWeight,
        textDecoration = textDecoration,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines
    )
}

@Composable
fun TextNormalSecondary(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE
) {
    TextNormal(
        text = text,
        modifier = modifier,
        color = color,
        style = Typography.bodyMedium,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines
    )
}

// ---- Small ----

@Composable
fun TextSmall(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE
) {
    TextNormal(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 9.sp,
        style = Typography.bodySmall,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines
    )
}