package com.rassvet.essential.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.rassvet.essential.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entrance = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        alpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        delay(220)
        onAnimationComplete()
    }
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(144.dp).scale(entrance.value),
            )
            Text(
                text = "Lazure",
                modifier = Modifier.padding(top = 20.dp).alpha(alpha.value),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, letterSpacing = (-0.5).sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.lazure_tagline),
                modifier = Modifier.padding(top = 6.dp).alpha(alpha.value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
