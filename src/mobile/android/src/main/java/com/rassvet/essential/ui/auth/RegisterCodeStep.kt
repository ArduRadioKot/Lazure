package com.rassvet.essential.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R
import com.rassvet.essential.ui.theme.EssentialDisplayFontFamily

@Composable
internal fun RegisterCodeStep(
    email: String,
    code: String,
    onCodeChange: (String) -> Unit,
    busy: Boolean,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val digits = code.filter { it.isDigit() }.take(6)
    val boxShape = RoundedCornerShape(12.dp)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.auth_verify_title),
            modifier = Modifier.fillMaxWidth(),
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = EssentialDisplayFontFamily,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Normal,
                ),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.auth_verify_subtitle, email),
            modifier = Modifier.fillMaxWidth(),
            color = AuthUi.MutedGray,
            style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(32.dp))

        BasicTextField(
            value = digits,
            onValueChange = { raw ->
                onCodeChange(raw.filter { it.isDigit() }.take(6))
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            textStyle =
                TextStyle(
                    color = AuthUi.TextWhite.copy(alpha = 0.01f),
                    fontSize = 1.sp,
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            cursorBrush = SolidColor(AuthUi.TextWhite),
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    repeat(6) { index ->
                        val char = digits.getOrNull(index)?.toString().orEmpty()
                        val filled = index < digits.length
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 46.dp, height = 56.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (filled) MaterialTheme.colorScheme.primary else AuthUi.BorderGray,
                                        shape = boxShape,
                                    )
                                    .background(AuthUi.LoginBg, boxShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = char,
                                color = AuthUi.TextWhite,
                                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onConfirm,
            enabled = !busy && digits.length == 6,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            shape = AuthUi.ButtonShape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_verify_confirm),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onResend,
            enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = AuthUi.MutedGray),
        ) {
            Text(
                text = stringResource(R.string.auth_verify_resend),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )
        }

        TextButton(
            onClick = onBack,
            enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(
                text = stringResource(R.string.auth_verify_back),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}


