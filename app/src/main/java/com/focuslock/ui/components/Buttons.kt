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

@Composable
fun CyberToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String = "", modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }

    var thumbX by remember { mutableStateOf(if (checked) 24f else 0f) }
    
    LaunchedEffect(checked) {
        val target = if (checked) 24f else 0f
        if (checked) {
            thumbX = 10f
            delay(40)
            thumbX = 6f
            delay(40)
            thumbX = 14f
            delay(40)
        } else {
            thumbX = 14f
            delay(40)
            thumbX = 18f
            delay(40)
            thumbX = 10f
            delay(40)
        }
        thumbX = target
    }

    val animatedThumbX by animateFloatAsState(
        targetValue = thumbX,
        animationSpec = tween(100),
        label = "thumb_x"
    )

    Row(modifier = modifier.clickable(interactionSource = interactionSource, indication = null) { onCheckedChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) {
            Text(text = label, style = FocusTypography.bodyMedium, color = TextPrimary)
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Box(
            modifier = Modifier
                .size(52.dp, 28.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, if (checked) Color.Transparent else CyanDim, RoundedCornerShape(50))
                .drawBehind {
                    drawRect(color = if (checked) CyanPrimary else SurfaceDark)
                    
                    if (animatedThumbX > 0f && animatedThumbX < 24f) {
                        drawLine(
                            color = CyanPrimary.copy(alpha = 0.5f),
                            start = Offset(14.dp.toPx(), size.height / 2),
                            end = Offset(animatedThumbX.dp.toPx() + 14.dp.toPx(), size.height / 2),
                            strokeWidth = 20.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedThumbX.dp.roundToPx(), 0) }
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .drawBehind { drawRect(color = if (checked) DeepVoid else TextPrimary) }
            )
        }
    }
}

@Composable
fun NavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) CyanPrimary else TextSecondary,
        animationSpec = tween(200),
        label = "icon_color"
    )

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor
        )
        
        AnimatedVisibility(visible = selected) {
            Text(
                text = label.uppercase(),
                style = FocusTypography.labelSmall,
                color = CyanPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        if (selected) {
            Canvas(modifier = Modifier.size(24.dp, 2.dp)) {
                val gradient = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.5f to CyanPrimary,
                    1.0f to Color.Transparent
                )
                drawLine(
                    brush = gradient,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height
                )
            }
        } else {
            Spacer(modifier = Modifier.size(24.dp, 2.dp))
        }
    }
}
