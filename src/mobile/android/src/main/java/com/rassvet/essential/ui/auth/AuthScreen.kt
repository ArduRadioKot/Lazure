package com.rassvet.essential.ui.auth

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.BuildConfig
import com.rassvet.essential.R
import com.rassvet.essential.data.api.DEV_STUB_AUTH_TOKEN
import com.rassvet.essential.data.api.EssentialApi
import com.rassvet.essential.data.api.EssentialHttpException
import com.rassvet.essential.data.api.fetchAndCacheMeAccount
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.ui.theme.EssentialDisplayFontFamily
import java.io.InterruptedIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

private fun Throwable.isConnectivityFailure(): Boolean {
    var c: Throwable? = this
    while (c != null) {
        when (c) {
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is java.net.NoRouteToHostException,
            is InterruptedIOException,
            -> return true
        }
        c = c.cause
    }
    return false
}

private enum class AuthMode {
    Login,
    Register,
}


private const val DevStubLogin = "admin"
private const val DevStubPassword = "admin"

private fun matchesDevStubLogin(
    mode: AuthMode,
    trimmedLoginOrEmail: String,
    password: String,
): Boolean =
    BuildConfig.DEBUG &&
        mode == AuthMode.Login &&
        trimmedLoginOrEmail == DevStubLogin &&
        password == DevStubPassword

private sealed class AuthStep {
    data class Credentials(val mode: AuthMode) : AuthStep()

    data class VerifyEmail(
        val login: String,
        val email: String,
        val password: String,
    ) : AuthStep()

    data class ForgotIdentifier(val prefilled: String = "") : AuthStep()

    data class ForgotVerifyCode(
        val email: String,
        val loginOrEmail: String,
    ) : AuthStep()

    data class ForgotNewPassword(
        val email: String,
        val code: String,
        val loginOrEmail: String,
    ) : AuthStep()
}

private fun mapAuthError(context: android.content.Context, e: Throwable): String =
    when (e) {
        is EssentialHttpException -> {
            val body = e.responseBody.orEmpty()
            when {
                body.contains("email_taken") -> context.getString(R.string.auth_error_email_taken)
                body.contains("login_taken") -> context.getString(R.string.auth_error_login_taken)
                body.contains("invalid_login") -> context.getString(R.string.auth_error_invalid_login)
                body.contains("weak_password") -> context.getString(R.string.auth_error_weak_password)
                body.contains("invalid_credentials") -> context.getString(R.string.auth_error_credentials)
                body.contains("invalid_code") -> context.getString(R.string.auth_error_invalid_code)
                body.contains("code_expired") -> context.getString(R.string.auth_error_code_expired)
                body.contains("rate_limited") -> context.getString(R.string.auth_error_rate_limited)
                body.contains("reset_not_found") -> context.getString(R.string.auth_error_reset_not_found)
                else -> context.getString(R.string.auth_error_generic)
            }
        }
        is InterruptedIOException,
        is java.net.SocketTimeoutException,
        -> context.getString(R.string.auth_error_network)
        is SSLHandshakeException,
        is SSLException,
        -> context.getString(R.string.auth_error_ssl)
        else ->
            when {
                e.isConnectivityFailure() -> context.getString(R.string.auth_error_network)
                else -> context.getString(R.string.auth_error_generic)
            }
    }

@Composable
fun AuthScreen(
    prefs: VaultPreferencesRepository,
    onAuthenticated: (isRegister: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var authStep by remember { mutableStateOf<AuthStep>(AuthStep.Credentials(AuthMode.Login)) }
    var verifyCode by remember { mutableStateOf("") }
    var loginOrEmail by remember { mutableStateOf("") }
    var registerLogin by remember { mutableStateOf("") }
    var registerEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var resetIdentifier by remember { mutableStateOf("") }
    var resetNewPassword by remember { mutableStateOf("") }
    var resetConfirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var bottomBlockHeight by remember { mutableStateOf(0.dp) }

    val mode =
        when (val step = authStep) {
            is AuthStep.Credentials -> step.mode
            is AuthStep.VerifyEmail -> AuthMode.Register
            is AuthStep.ForgotIdentifier,
            is AuthStep.ForgotVerifyCode,
            is AuthStep.ForgotNewPassword,
            -> AuthMode.Login
        }

    val fixedApiBase = context.getString(R.string.default_api_base)

    LaunchedEffect(Unit) {
        prefs.setApiBaseUrl(fixedApiBase)
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AuthUi.LoginBg)
                .padding(horizontal = 28.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 96.dp, bottom = bottomBlockHeight + 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            when (val step = authStep) {
                is AuthStep.ForgotIdentifier -> {
                    ForgotIdentifierStep(
                        loginOrEmail = resetIdentifier,
                        onLoginOrEmailChange = { resetIdentifier = it },
                        busy = busy,
                        onSendCode = {
                            if (busy) return@ForgotIdentifierStep
                            val id = resetIdentifier.trim()
                            if (id.isBlank()) return@ForgotIdentifierStep
                            busy = true
                            scope.launch {
                                try {
                                    val nextStep: AuthStep? =
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                prefs.setApiBaseUrl(fixedApiBase)
                                                val api = EssentialApi(fixedApiBase)
                                                try {
                                                    api.passwordResetSendCode(id)
                                                } finally {
                                                    api.close()
                                                }
                                            }.fold(
                                                onSuccess = { response ->
                                                    val email = response.email?.trim().orEmpty()
                                                    if (email.isNotEmpty()) {
                                                        verifyCode = ""
                                                        AuthStep.ForgotVerifyCode(
                                                            email = email,
                                                            loginOrEmail = id,
                                                        )
                                                    } else {
                                                        null
                                                    }
                                                },
                                                onFailure = { throw it },
                                            )
                                        }
                                    when {
                                        nextStep != null -> authStep = nextStep
                                        else ->
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.auth_reset_code_sent_generic),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                    }
                                } catch (t: Throwable) {
                                    Toast.makeText(
                                        context,
                                        mapAuthError(context, t),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onBack = {
                            authStep = AuthStep.Credentials(AuthMode.Login)
                        },
                    )
                }
                is AuthStep.ForgotVerifyCode -> {
                    RegisterCodeStep(
                        email = step.email,
                        code = verifyCode,
                        onCodeChange = { verifyCode = it },
                        busy = busy,
                        onConfirm = {
                            val digits = verifyCode.filter { it.isDigit() }
                            if (digits.length != 6) return@RegisterCodeStep
                            authStep =
                                AuthStep.ForgotNewPassword(
                                    email = step.email,
                                    code = digits,
                                    loginOrEmail = step.loginOrEmail,
                                )
                            resetNewPassword = ""
                            resetConfirmPassword = ""
                        },
                        onResend = {
                            if (busy) return@RegisterCodeStep
                            busy = true
                            scope.launch {
                                try {
                                    val failureMsg: String? =
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                prefs.setApiBaseUrl(fixedApiBase)
                                                val api = EssentialApi(fixedApiBase)
                                                try {
                                                    api.passwordResetSendCode(step.loginOrEmail)
                                                } finally {
                                                    api.close()
                                                }
                                            }.exceptionOrNull()?.let { mapAuthError(context, it) }
                                        }
                                    if (failureMsg != null) {
                                        Toast.makeText(context, failureMsg, Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.auth_code_sent),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onBack = {
                            verifyCode = ""
                            authStep = AuthStep.ForgotIdentifier(step.loginOrEmail)
                        },
                    )
                }
                is AuthStep.ForgotNewPassword -> {
                    PasswordResetNewPasswordStep(
                        email = step.email,
                        newPassword = resetNewPassword,
                        confirmPassword = resetConfirmPassword,
                        onNewPasswordChange = { resetNewPassword = it },
                        onConfirmPasswordChange = { resetConfirmPassword = it },
                        busy = busy,
                        onSubmit = {
                            if (busy) return@PasswordResetNewPasswordStep
                            if (resetNewPassword != resetConfirmPassword) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.auth_error_password_mismatch),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@PasswordResetNewPasswordStep
                            }
                            busy = true
                            scope.launch {
                                try {
                                    val failureMsg: String? =
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                prefs.setApiBaseUrl(fixedApiBase)
                                                val api = EssentialApi(fixedApiBase)
                                                try {
                                                    val t =
                                                        api.passwordResetConfirm(
                                                            step.email,
                                                            step.code,
                                                            resetNewPassword,
                                                        )
                                                    prefs.setAuthToken(t.accessToken, step.email)
                                                    runCatching {
                                                        prefs.fetchAndCacheMeAccount(fixedApiBase, t.accessToken)
                                                    }
                                                } finally {
                                                    api.close()
                                                }
                                            }.exceptionOrNull()?.let { mapAuthError(context, it) }
                                        }
                                    if (failureMsg != null) {
                                        Toast.makeText(context, failureMsg, Toast.LENGTH_LONG).show()
                                    } else {
                                        onAuthenticated(false)
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onBack = {
                            authStep =
                                AuthStep.ForgotVerifyCode(
                                    email = step.email,
                                    loginOrEmail = step.loginOrEmail,
                                )
                        },
                    )
                }
                is AuthStep.VerifyEmail -> {
                    RegisterCodeStep(
                        email = step.email,
                        code = verifyCode,
                        onCodeChange = { verifyCode = it },
                        busy = busy,
                        onConfirm = {
                            if (busy) return@RegisterCodeStep
                            busy = true
                            scope.launch {
                                try {
                                    val failureMsg: String? =
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                prefs.setApiBaseUrl(fixedApiBase)
                                                val api = EssentialApi(fixedApiBase)
                                                try {
                                                    val t = api.registerConfirm(step.email, verifyCode)
                                                    prefs.setAuthTokenAfterRegister(t.accessToken, step.email)
                                                    runCatching {
                                                        prefs.fetchAndCacheMeAccount(fixedApiBase, t.accessToken)
                                                    }
                                                } finally {
                                                    api.close()
                                                }
                                            }.exceptionOrNull()?.let { mapAuthError(context, it) }
                                        }
                                    if (failureMsg != null) {
                                        Toast.makeText(context, failureMsg, Toast.LENGTH_LONG).show()
                                    } else {
                                        onAuthenticated(true)
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onResend = {
                            if (busy) return@RegisterCodeStep
                            busy = true
                            scope.launch {
                                try {
                                    val failureMsg: String? =
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                prefs.setApiBaseUrl(fixedApiBase)
                                                val api = EssentialApi(fixedApiBase)
                                                try {
                                                    api.registerSendCode(step.email, step.password, step.login)
                                                } finally {
                                                    api.close()
                                                }
                                            }.exceptionOrNull()?.let { mapAuthError(context, it) }
                                        }
                                    if (failureMsg != null) {
                                        Toast.makeText(context, failureMsg, Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.auth_code_sent), Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onBack = {
                            registerLogin = step.login
                            registerEmail = step.email
                            password = step.password
                            verifyCode = ""
                            authStep = AuthStep.Credentials(AuthMode.Register)
                        },
                    )
                }
                is AuthStep.Credentials -> {
            Text(
                text = stringResource(R.string.auth_title),
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

            Spacer(modifier = Modifier.height(8.dp))

            if (mode == AuthMode.Login) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = loginOrEmail,
                    onValueChange = { loginOrEmail = it },
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = AuthUi.TextWhite,
                            fontWeight = FontWeight.Normal,
                        ),
                    label = {
                        Text(
                            stringResource(R.string.auth_login_or_email_label),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Normal,
                                ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = AuthUi.FieldShape,
                    colors = authFieldColors(),
                )
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = registerLogin,
                    onValueChange = { registerLogin = it },
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = AuthUi.TextWhite,
                            fontWeight = FontWeight.Normal,
                        ),
                    label = {
                        Text(
                            stringResource(R.string.auth_login_label),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Normal,
                                ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = AuthUi.FieldShape,
                    colors = authFieldColors(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = registerEmail,
                    onValueChange = { registerEmail = it },
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = AuthUi.TextWhite,
                            fontWeight = FontWeight.Normal,
                        ),
                    label = {
                        Text(
                            stringResource(R.string.auth_email_label),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Normal,
                                ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = AuthUi.FieldShape,
                    colors = authFieldColors(),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = password,
                onValueChange = { password = it },
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = AuthUi.TextWhite,
                        fontWeight = FontWeight.Normal,
                    ),
                label = {
                    Text(
                        stringResource(R.string.auth_password_label),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Normal,
                            ),
                    )
                },
                singleLine = true,
                visualTransformation =
                    if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            if (passwordVisible) {
                                KeyboardType.Text
                            } else {
                                KeyboardType.Password
                            },
                    ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector =
                                if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                            contentDescription =
                                stringResource(
                                    if (passwordVisible) {
                                        R.string.auth_hide_password
                                    } else {
                                        R.string.auth_show_password
                                    },
                                ),
                            tint = AuthUi.MutedGray,
                        )
                    }
                },
                shape = AuthUi.FieldShape,
                colors = authFieldColors(),
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (busy) return@Button
                    val pass = password
                    val loginIdentifier = loginOrEmail.trim()
                    val regLogin = registerLogin.trim()
                    val regEmail = registerEmail.trim()
                    val canSubmit =
                        when (mode) {
                            AuthMode.Login -> loginIdentifier.isNotBlank() && pass.isNotBlank()
                            AuthMode.Register ->
                                regLogin.isNotBlank() && regEmail.isNotBlank() && pass.isNotBlank()
                        }
                    if (!canSubmit) return@Button
                    busy = true
                    scope.launch {
                        try {
                            val failureMsg: String? =
                                withContext(Dispatchers.IO) {
                                    if (BuildConfig.DEBUG && matchesDevStubLogin(mode, loginIdentifier, pass)) {
                                        prefs.setApiBaseUrl(fixedApiBase)
                                        prefs.setAuthToken(DEV_STUB_AUTH_TOKEN, loginIdentifier)
                                        return@withContext null
                                    }
                                    runCatching {
                                        prefs.setApiBaseUrl(fixedApiBase)
                                        val api = EssentialApi(fixedApiBase)
                                        try {
                                            when (mode) {
                                                AuthMode.Login -> {
                                                    val t = api.login(loginIdentifier, pass)
                                                    prefs.setAuthToken(t.accessToken, loginIdentifier)
                                                    runCatching {
                                                        prefs.fetchAndCacheMeAccount(fixedApiBase, t.accessToken)
                                                    }
                                                }
                                                AuthMode.Register -> {
                                                    api.registerSendCode(regEmail, pass, regLogin)
                                                }
                                            }
                                        } finally {
                                            api.close()
                                        }
                                    }.exceptionOrNull()?.let { mapAuthError(context, it) }
                                }
                            if (failureMsg != null) {
                                Toast.makeText(context, failureMsg, Toast.LENGTH_LONG).show()
                            } else {
                                when (mode) {
                                    AuthMode.Login -> onAuthenticated(false)
                                    AuthMode.Register -> {
                                        Toast.makeText(context, context.getString(R.string.auth_code_sent), Toast.LENGTH_SHORT).show()
                                        authStep = AuthStep.VerifyEmail(regLogin, regEmail, pass)
                                        verifyCode = ""
                                    }
                                }
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
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
                    text =
                        when (mode) {
                            AuthMode.Login -> stringResource(R.string.auth_sign_in)
                            AuthMode.Register -> stringResource(R.string.auth_continue_register)
                        },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                )
            }

            if (mode == AuthMode.Login) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        resetIdentifier = loginOrEmail.trim()
                        resetNewPassword = ""
                        resetConfirmPassword = ""
                        verifyCode = ""
                        password = ""
                        authStep = AuthStep.ForgotIdentifier(loginOrEmail.trim())
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = AuthUi.MutedGray,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.auth_forgot_password),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, top = 4.dp)
                    .onSizeChanged {
                        if (authStep is AuthStep.Credentials) {
                            bottomBlockHeight = with(density) { it.height.toDp() }
                        } else {
                            bottomBlockHeight = 0.dp
                        }
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (authStep is AuthStep.Credentials) {
            OutlinedButton(
                onClick = {
                    verifyCode = ""
                    authStep =
                        if (mode == AuthMode.Login) {
                            AuthStep.Credentials(AuthMode.Register)
                        } else {
                            AuthStep.Credentials(AuthMode.Login)
                        }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                shape = AuthUi.ButtonShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = Color.Transparent,
                    ),
            ) {
                Text(
                    text =
                        when (mode) {
                            AuthMode.Login -> stringResource(R.string.auth_register)
                            AuthMode.Register -> stringResource(R.string.auth_back_to_login)
                        },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            com.rassvet.essential.ui.legal.AuthLegalFooter(
                mutedColor = AuthUi.MutedGray,
                linkColor = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.fillMaxWidth(),
                color = AuthUi.MutedGray.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
            }
        }
    }
}


