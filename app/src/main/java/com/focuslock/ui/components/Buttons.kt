package com.focuslock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.focuslock.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun createHexPath(size: androidx.compose.ui.geometry.Size): Path {
    val width = size.width
    val height = size.height
    val w4 = width / 4f
    val h2 = height / 2f
    return Path().apply {
        moveTo(w4, 0f)
        lineTo(width - w4, 0f)
        lineTo(width, h2)
        lineTo(width - w4, height)
        lineTo(w4, height)
        lineTo(0f, h2)
        close()
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (!enabled) DeepVoid else if (isPressed) CyanPrimary else DeepVoid
    val textColor = if (!enabled) TextSecondary else if (isPressed) DeepVoid else CyanPrimary
    val borderColor = if (!enabled) CyanDim else CyanPrimary

    var sweepTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed) {
        if (isPressed && enabled) {
            sweepTrigger = true
            delay(150)
            sweepTrigger = false
        }
    }

    val sweepProgress by animateFloatAsState(
        targetValue = if (sweepTrigger) 1f else 0f,
        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
        label = "sweep"
    )

    Box(
        modifier = modifier
            .semantics { contentDescription = label }
            .clip(PrimaryButtonShape)
            .border(1.5.dp, borderColor, PrimaryButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = if (enabled) CyberRippleIndication(CyanPrimary) else null,
                enabled = enabled,
                onClick = onClick
            )
            .drawBehind {
                drawRect(color = bgColor)
                if (enabled && !isPressed) {
                    drawRect(color = GlowCyan.copy(alpha = 0.2f))
                }
                if (!enabled) {
                    // Removed horizontal lines as requested
                }
                if (sweepProgress > 0f) {
                    val xPos = size.width * sweepProgress
                    drawLine(
                        color = CyanPrimary.copy(alpha = 0.2f),
                        start = Offset(xPos, 0f),
                        end = Offset(xPos, size.height),
                        strokeWidth = 20f
                    )
                }
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.uppercase(),
            style = FocusTypography.labelLarge,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
fun HexFAB(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val rotation by animateFloatAsState(
        targetValue = if (isPressed) 30f else 0f,
        animationSpec = tween(200),
        label = "hex_rotation"
    )

    var ringTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            ringTrigger = true
            delay(300)
            ringTrigger = false
        }
    }

    val ringScale by animateFloatAsState(
        targetValue = if (ringTrigger) 1.4f else 1.0f,
        animationSpec = tween(300),
        label = "ring_scale"
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (ringTrigger) 0f else 0.8f,
        animationSpec = tween(300),
        label = "ring_alpha"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .semantics { this.contentDescription = contentDescription }
            .graphicsLayer { rotationZ = rotation }
            .drawBehind {
                if (ringTrigger) {
                    drawPath(
                        path = createHexPath(size.copy(width = size.width * ringScale, height = size.height * ringScale)),
                        color = CyanPrimary.copy(alpha = ringAlpha),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            .clip(HexShape)
            .border(1.dp, CyanPrimary, HexShape)
            .clickable(
                interactionSource = interactionSource,
                indication = CyberRippleIndication(CyanPrimary),
                onClick = onClick
            )
            .drawBehind { drawRect(color = SurfaceElevated) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyanPrimary.copy(alpha = if (isPressed) 1f else 0.6f)
        )
    }
}



