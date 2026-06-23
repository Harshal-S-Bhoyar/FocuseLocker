package com.focuslock.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val PrimaryButtonShape = GenericShape { size, _ ->
    // Actually, Shape in Compose handles bounds. We will define an absolute cut of 48f (approx 16dp).
    val cut = 48f
    moveTo(0f, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, cut)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

val GhostButtonShape = RoundedCornerShape(2.dp)

val HexShape = GenericShape { size, _ ->
    val width = size.width
    val height = size.height
    val w4 = width / 4f
    val h2 = height / 2f
    moveTo(w4, 0f)
    lineTo(width - w4, 0f)
    lineTo(width, h2)
    lineTo(width - w4, height)
    lineTo(w4, height)
    lineTo(0f, h2)
    close()
}
