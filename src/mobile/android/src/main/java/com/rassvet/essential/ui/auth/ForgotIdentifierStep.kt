package com.rassvet.essential.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
internal fun ForgotIdentifierStep(
    loginOrEmail: String,
    onLoginOrEmailChange: (String) -> Unit,
    busy: Boolean,
    onSendCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.auth_reset_title),
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
            text = stringResource(R.string.auth_reset_identifier_hint),
            modifier = Modifier.fillMaxWidth(),
            color = AuthUi.MutedGray,
            style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = loginOrEmail,
            onValueChange = onLoginOrEmailChange,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = AuthUi.TextWhite,
                    fontWeight = FontWeight.Normal,
                ),
            label = {
                Text(
                    stringResource(R.string.auth_login_or_email_label),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = AuthUi.FieldShape,
            colors = authFieldColors(),
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onSendCode,
            enabled = !busy && loginOrEmail.isNotBlank(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            shape = AuthUi.ButtonShape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_reset_send_code),
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
                text = stringResource(R.string.auth_back_to_login),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}


