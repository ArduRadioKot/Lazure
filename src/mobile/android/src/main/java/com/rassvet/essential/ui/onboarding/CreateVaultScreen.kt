package com.rassvet.essential.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rassvet.essential.BuildConfig
import com.rassvet.essential.R
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.vault.VaultDocuments
import com.rassvet.essential.ui.theme.EssentialDisplayFontFamily
import com.rassvet.essential.ui.theme.MontFontFamily
import java.io.File
import kotlinx.coroutines.launch

private val ScreenBg = Color(0xFF000000)
private val MutedGray = Color(0xFF8E8E93)
private val TextWhite = Color(0xFFFFFFFF)
private val BorderGray = Color(0xFF8E8E93)
private val FieldShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(14.dp)

private fun sanitizeVaultSlug(raw: String): String {
    val slug =
        raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "_")
            .trim('_', '.')
    return slug.ifBlank { "my_vault" }.take(64)
}

@Composable
fun CreateVaultScreen(
    prefs: VaultPreferencesRepository,
    onVaultReady: () -> Unit,
    onBack: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var vaultName by remember { mutableStateOf("my_vault") }
    var bottomBlockHeight by remember { mutableStateOf(0.dp) }

    val fieldColors =
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite,
            focusedBorderColor = BorderGray,
            unfocusedBorderColor = BorderGray,
            cursorColor = TextWhite,
            focusedLabelColor = MutedGray,
            unfocusedLabelColor = MutedGray,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        )

    fun submit() {
        val slug = sanitizeVaultSlug(vaultName)
        val dir = File(context.filesDir, "vaults/$slug")
        if (dir.exists()) {
            Toast.makeText(context, R.string.create_vault_error_exists, Toast.LENGTH_LONG).show()
            return
        }
        if (!dir.mkdirs()) {
            Toast.makeText(context, R.string.auth_error_generic, Toast.LENGTH_LONG).show()
            return
        }
        val titleLine = vaultName.trim().ifBlank { slug }
        runCatching {
            File(dir, "Welcome.md").writeText("# $titleLine\n\n", Charsets.UTF_8)
        }
        scope.launch {
            prefs.setVaultTreeUri("${VaultDocuments.INTERNAL_PREFIX}$slug")
            onVaultReady()
        }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(horizontal = 28.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 56.dp, bottom = bottomBlockHeight + 8.dp),
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.create_vault_headline),
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        color = accent,
                        fontFamily = EssentialDisplayFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 32.sp,
                    ),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = vaultName,
                onValueChange = { vaultName = it },
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = TextWhite,
                        fontWeight = FontWeight.Normal,
                    ),
                label = {
                    Text(
                        stringResource(R.string.create_vault_name_label),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Normal,
                            ),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
                shape = FieldShape,
                colors = fieldColors,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.create_vault_location_section),
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontFamily = MontFontFamily,
                        color = MutedGray,
                        fontWeight = FontWeight.Normal,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.create_vault_storage_app_hint),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MontFontFamily,
                        color = MutedGray,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 18.sp,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { submit() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                shape = ButtonShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.create_vault_submit),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                )
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
                        bottomBlockHeight = with(density) { it.height.toDp() }
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.fillMaxWidth(),
                color = MutedGray.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 4.dp, top = 4.dp)
                    .zIndex(1f)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.cd_back),
                fontFamily = EssentialDisplayFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = accent,
            )
        }
    }
}


