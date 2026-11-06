@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.rassvet.essential.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.ui.theme.EssentialDisplayFontFamily
import kotlinx.coroutines.delay

@Composable
fun PostRegisterWelcomeScreen(
    prefs: VaultPreferencesRepository,
    onContinue: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        revealed = true
    }
    LaunchedEffect(Unit) {
        delay(2400)
        prefs.setNeedsRegisterWelcome(false)
        onContinue()
    }

    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.88f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "post_register_scale",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "post_register_alpha",
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = contentAlpha
                    }
                    .padding(horizontal = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_title),
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = EssentialDisplayFontFamily,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.post_register_message),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}


