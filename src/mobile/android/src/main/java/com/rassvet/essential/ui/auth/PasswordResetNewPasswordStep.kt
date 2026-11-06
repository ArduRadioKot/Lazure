package com.rassvet.essential.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R
import com.rassvet.essential.ui.theme.EssentialDisplayFontFamily

@Composable
internal fun PasswordResetNewPasswordStep(
    email: String,
    newPassword: String,
    confirmPassword: String,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    busy: Boolean,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var newVisible by rememberSaveable { mutableStateOf(false) }
    var confirmVisible by rememberSaveable { mutableStateOf(false) }
    val canSubmit =
        newPassword.length >= 8 &&
            confirmPassword.length >= 8 &&
            newPassword == confirmPassword

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.auth_reset_password_title),
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
            text = stringResource(R.string.auth_reset_password_subtitle, email),
            modifier = Modifier.fillMaxWidth(),
            color = AuthUi.MutedGray,
            style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(24.dp))

        AuthPasswordField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = stringResource(R.string.auth_reset_new_password),
            visible = newVisible,
            onToggleVisible = { newVisible = !newVisible },
        )

        Spacer(modifier = Modifier.height(6.dp))

        AuthPasswordField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = stringResource(R.string.auth_reset_confirm_password),
            visible = confirmVisible,
            onToggleVisible = { confirmVisible = !confirmVisible },
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onSubmit,
            enabled = !busy && canSubmit,
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
                text = stringResource(R.string.auth_reset_save_and_login),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onBack,
            enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(
                text = stringResource(R.string.auth_reset_back_to_code),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        textStyle =
            MaterialTheme.typography.bodyLarge.copy(
                color = AuthUi.TextWhite,
                fontWeight = FontWeight.Normal,
            ),
        label = {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
            )
        },
        singleLine = true,
        visualTransformation =
            if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    if (visible) {
                        KeyboardType.Text
                    } else {
                        KeyboardType.Password
                    },
            ),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector =
                        if (visible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                    contentDescription = null,
                    tint = AuthUi.MutedGray,
                )
            }
        },
        shape = AuthUi.FieldShape,
        colors = authFieldColors(),
    )
}


