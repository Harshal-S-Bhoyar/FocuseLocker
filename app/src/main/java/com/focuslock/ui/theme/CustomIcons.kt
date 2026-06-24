package com.focuslock.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object CustomIcons {
    val Remove: ImageVector
        get() = ImageVector.Builder(
            name = "Remove",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 13f)
                lineTo(5f, 13f)
                lineTo(5f, 11f)
                lineTo(19f, 11f)
                close()
            }
        }.build()

    val HourglassEmpty: ImageVector
        get() = ImageVector.Builder(
            name = "HourglassEmpty",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 2f)
                lineTo(18f, 2f)
                lineTo(18f, 8f)
                lineTo(14f, 12f)
                lineTo(18f, 16f)
                lineTo(18f, 22f)
                lineTo(6f, 22f)
                lineTo(6f, 16f)
                lineTo(10f, 12f)
                lineTo(6f, 8f)
                close()
                moveTo(8f, 16.5f)
                lineTo(8f, 20f)
                lineTo(16f, 20f)
                lineTo(16f, 16.5f)
                lineTo(12f, 12.5f)
                close()
                moveTo(16f, 7.5f)
                lineTo(16f, 4f)
                lineTo(8f, 4f)
                lineTo(8f, 7.5f)
                lineTo(12f, 11.5f)
                close()
            }
        }.build()
}
