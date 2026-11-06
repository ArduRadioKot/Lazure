package com.rassvet.essential.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.rassvet.essential.BuildConfig
import com.rassvet.essential.R
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.vault.VaultDocuments
import com.rassvet.essential.ui.AppRoutes
import com.rassvet.essential.ui.components.InternalVaultPickerList
import com.rassvet.essential.ui.theme.EssentialDisplayFontFamily
import com.rassvet.essential.ui.theme.MontFontFamily
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val OnboardingBg = Color(0xFF000000)
private val MutedGray = Color(0xFF8E8E93)
private val TextWhite = Color(0xFFFFFFFF)
private val ButtonShape = RoundedCornerShape(14.dp)

@Composable
fun OnboardingScreen(
    prefs: VaultPreferencesRepository,
    navController: NavHostController,
    onVaultPickedToMain: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var bottomBlockHeight by remember { mutableStateOf(0.dp) }
    val vaultStored by prefs.vaultTreeUri.collectAsState(initial = null)

    val vaultSlugs =
        remember {
            val root = File(context.filesDir, "vaults")
            if (!root.isDirectory) {
                emptyList()
            } else {
                root.listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { it.name }
                    ?.sorted()
                    .orEmpty()
            }
        }

    val currentSlug =
        vaultStored
            ?.trim()
            ?.takeIf { it.startsWith(VaultDocuments.INTERNAL_PREFIX) }
            ?.removePrefix(VaultDocuments.INTERNAL_PREFIX)

    var selectedSlug by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vaultSlugs, currentSlug) {
        selectedSlug =
            when {
                currentSlug != null && currentSlug in vaultSlugs -> currentSlug
                vaultSlugs.size == 1 -> vaultSlugs.first()
                else -> selectedSlug?.takeIf { it in vaultSlugs }
            }
    }

    fun confirmVault(slug: String) {
        scope.launch {
            prefs.setVaultTreeUri("${VaultDocuments.INTERNAL_PREFIX}$slug")
            onVaultPickedToMain()
        }
    }

    val bodyAnnotated =
        buildAnnotatedString {
            append(stringResource(R.string.onboarding_rich_part1))
            pushStyle(SpanStyle(color = accent, fontWeight = FontWeight.Normal))
            append(stringResource(R.string.onboarding_rich_offline))
            pop()
            append(stringResource(R.string.onboarding_rich_part2))
            pushStyle(SpanStyle(color = accent, fontWeight = FontWeight.Normal))
            append(stringResource(R.string.onboarding_rich_vault))
            pop()
            append(stringResource(R.string.onboarding_rich_suffix))
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(OnboardingBg)
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
                text = stringResource(R.string.auth_title),
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        color = accent,
                        fontFamily = EssentialDisplayFontFamily,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.onboarding_hero_headline),
                modifier = Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = MontFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = TextWhite,
                        lineHeight = 28.sp,
                    ),
                textAlign = TextAlign.Start,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = bodyAnnotated,
                modifier = Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = MontFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = TextWhite,
                        lineHeight = 24.sp,
                    ),
                textAlign = TextAlign.Start,
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (vaultSlugs.isNotEmpty()) {
                InternalVaultPickerList(
                    vaultSlugs = vaultSlugs,
                    selectedSlug = selectedSlug,
                    currentSlug = currentSlug,
                    onSelect = { selectedSlug = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (vaultSlugs.isNotEmpty()) {
                Button(
                    onClick = {
                        val slug = selectedSlug ?: vaultSlugs.firstOrNull() ?: return@Button
                        confirmVault(slug)
                    },
                    enabled = selectedSlug != null || vaultSlugs.isNotEmpty(),
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
                        text = stringResource(R.string.onboarding_use_existing),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = { navController.navigate(AppRoutes.CreateVault) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                shape = ButtonShape,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_create_vault),
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

    }
}


