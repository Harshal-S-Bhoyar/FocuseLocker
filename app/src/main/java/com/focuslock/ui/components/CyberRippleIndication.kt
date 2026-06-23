package com.focuslock.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll

class CyberRippleIndication(val glowColor: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return CyberRippleNode(interactionSource, glowColor)
    }
    
    override fun equals(other: Any?): Boolean =
        other === this || (other is CyberRippleIndication && glowColor == other.glowColor)

    override fun hashCode(): Int = glowColor.hashCode()
}

class CyberRippleNode(
    private val interactionSource: InteractionSource,
    private val color: Color
) : Modifier.Node(), DrawModifierNode {

    private val ripples = mutableListOf<RippleData>()

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> addRipple(interaction)
                }
            }
        }
    }

    private fun addRipple(press: PressInteraction.Press) {
        if (ripples.size >= 10) return // VULN-001: Prevent unbound memory growth
        val ripple = RippleData(press.pressPosition)
        ripples.add(ripple)
        coroutineScope.launch {
            val job1 = launch {
                ripple.radius.animateTo(40f, animationSpec = tween(300))
            }
            val job2 = launch {
                ripple.alpha.animateTo(0f, animationSpec = tween(300))
            }
            joinAll(job1, job2)
            ripples.remove(ripple)
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        ripples.forEach { ripple ->
            drawCircle(
                color = color.copy(alpha = ripple.alpha.value),
                radius = ripple.radius.value * density,
                center = ripple.position
            )
        }
    }

    private class RippleData(val position: Offset) {
        val radius = Animatable(0f)
        val alpha = Animatable(0.6f)
    }
}
