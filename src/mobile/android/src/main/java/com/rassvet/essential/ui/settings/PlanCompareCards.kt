package com.rassvet.essential.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PlanCompareCards(
    freeCloudQuota: Long,
    proCloudQuota: Long,
    proPriceRub: Int,
    activePro: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.settings_plan_compare_title),
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlanTierCard(
                title = stringResource(R.string.settings_plan_free_title),
                cloudQuota = freeCloudQuota,
                priceLabel = null,
                highlighted = !activePro,
                modifier = Modifier.weight(1f),
            )
            PlanTierCard(
                title = stringResource(R.string.settings_plan_pro_title),
                cloudQuota = proCloudQuota,
                priceLabel = stringResource(R.string.settings_plan_pro_price, proPriceRub),
                highlighted = activePro,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlanTierCard(
    title: String,
    cloudQuota: Long,
    priceLabel: String?,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color =
            if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        border =
            if (highlighted) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    color =
                        if (highlighted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
                if (priceLabel != null) {
                    Text(
                        text = priceLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal),
                    )
                }
            }
            PlanFeatureRow(
                icon = {
                    Icon(
                        Icons.Outlined.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                text = stringResource(R.string.settings_plan_local_unlimited),
            )
            PlanFeatureRow(
                icon = {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                },
                text =
                    stringResource(
                        R.string.settings_plan_cloud_daily,
                        formatCloudQuotaAmount(cloudQuota),
                    ),
            )
        }
    }
}

@Composable
private fun PlanFeatureRow(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun formatCloudQuotaAmount(quota: Long): String {
    val amount =
        NumberFormat.getIntegerInstance(Locale("ru", "RU")).format(quota.coerceAtLeast(0L))
    return stringResource(R.string.settings_plan_quota_amount, amount)
}


