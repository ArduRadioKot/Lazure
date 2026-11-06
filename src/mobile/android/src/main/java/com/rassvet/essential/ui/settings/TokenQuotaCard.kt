package com.rassvet.essential.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
fun TokenQuotaCard(
    planLabel: String,
    tokensUsed: Long,
    tokensQuota: Long,
    hasSubscription: Boolean,
    subscriptionExpiresAtEpochMs: Long? = null,
    modifier: Modifier = Modifier,
) {
    val formatter = NumberFormat.getIntegerInstance(Locale("ru", "RU"))
    val usedLabel = formatter.format(tokensUsed)
    val unlimitedLabel = stringResource(R.string.settings_quota_unlimited)
    val quotaLabel =
        if (tokensQuota > 0L) {
            formatter.format(tokensQuota)
        } else {
            unlimitedLabel
        }
    val progress =
        if (tokensQuota > 0L) {
            min(1f, tokensUsed.toFloat() / tokensQuota.toFloat())
        } else {
            0f
        }
    val progressColor =
        when {
            !hasSubscription -> MaterialTheme.colorScheme.onSurfaceVariant
            progress >= 0.95f -> MaterialTheme.colorScheme.error
            progress >= 0.8f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_quota_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        text = planLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TextStyle(fontSize = 12.sp),
                    )
                    if (hasSubscription) {
                        val expiryLabel =
                            when {
                                subscriptionExpiresAtEpochMs == null || subscriptionExpiresAtEpochMs <= 0L ->
                                    stringResource(R.string.settings_subscription_forever)
                                else -> {
                                    val dateLabel =
                                        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                                            .format(Date(subscriptionExpiresAtEpochMs))
                                    stringResource(R.string.settings_subscription_until, dateLabel)
                                }
                            }
                        Text(
                            text = expiryLabel,
                            color = MaterialTheme.colorScheme.primary,
                            style = TextStyle(fontSize = 12.sp),
                        )
                    }
                }
                Text(
                    text = "$usedLabel / $quotaLabel",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }

            if (tokensQuota > 0L) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}


