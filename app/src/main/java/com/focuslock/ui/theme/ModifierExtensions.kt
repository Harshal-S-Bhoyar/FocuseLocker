package com.focuslock.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

enum class GlowEdge { Top, Bottom, Left, Right }

fun Modifier.cyberSurface(glowColor: Color = GlowCyan): Modifier = this.drawBehind {
    // Noise/grain overlay — placeholder implementation for a 4% alpha grain
    // In a full implementation, you would draw a repeating noise bitmap shader here.
    drawRect(color = glowColor.copy(alpha = 0.04f))
}

fun Modifier.edgeGlow(color: Color, edge: GlowEdge = GlowEdge.Bottom): Modifier = this.drawBehind {
    val thickness = 2.dp.toPx()
    when (edge) {
        GlowEdge.Top -> drawRect(color = color, size = size.copy(height = thickness))
        GlowEdge.Bottom -> drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - thickness), size = size.copy(height = thickness))
        GlowEdge.Left -> drawRect(color = color, size = size.copy(width = thickness))
        GlowEdge.Right -> drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(size.width - thickness, 0f), size = size.copy(width = thickness))
    }
}

fun Modifier.glassMorphism(tint: Color = SurfaceDark.copy(alpha = 0.85f)): Modifier = this.graphicsLayer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        renderEffect = RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP).asComposeRenderEffect()
        // We cannot easily draw a color tint directly in RenderEffect without a ColorFilter wrapper,
        // but typically the glassmorphism surface handles the tint via its background property after applying this modifier.
    }
    alpha = 0.99f // Force layer
}
