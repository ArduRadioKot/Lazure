package com.rassvet.essential.ui.auth

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object AuthUi {
    val LoginBg = Color(0xFF000000)
    val BorderGray = Color(0xFF8E8E93)
    val MutedGray = Color(0xFF8E8E93)
    val TextWhite = Color(0xFFFFFFFF)

    val FieldShape = RoundedCornerShape(14.dp)
    val ButtonShape = RoundedCornerShape(14.dp)

}

@Composable
internal fun authFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = AuthUi.TextWhite,
        unfocusedTextColor = AuthUi.TextWhite,
        focusedBorderColor = AuthUi.BorderGray,
        unfocusedBorderColor = AuthUi.BorderGray,
        cursorColor = AuthUi.TextWhite,
        focusedLabelColor = AuthUi.MutedGray,
        unfocusedLabelColor = AuthUi.MutedGray,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )


