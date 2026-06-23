package com.focuslock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.sp

// Note: In a production setup, ShareTechMono, Orbitron, and Inter should be configured
// via Google Fonts Compose. We are using standard Monospace/SansSerif families as fallbacks
// to ensure it compiles before dependencies are added.
val ShareTechMono = FontFamily.Monospace
val Orbitron = FontFamily.SansSerif
val Inter = FontFamily.SansSerif


val FocusTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ShareTechMono, 
        fontSize = 32.sp, 
        letterSpacing = 2.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Orbitron,      
        fontSize = 20.sp, 
        fontWeight = FontWeight.Bold
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,         
        fontSize = 16.sp, 
        fontWeight = FontWeight.Light
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontFamily = ShareTechMono,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold
    ),
    labelSmall = TextStyle(
        fontFamily = ShareTechMono, 
        fontSize = 10.sp, 
        letterSpacing = 3.sp
        // Note: textTransform uppercase is usually handled in Text composable or via String.uppercase()
        // but we can enforce it functionally where labelSmall is used.
    )
)
