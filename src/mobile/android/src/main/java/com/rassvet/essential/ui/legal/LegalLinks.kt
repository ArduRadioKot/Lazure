package com.rassvet.essential.ui.legal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R

fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

fun Context.openSupportEmail() {
    val email = getString(R.string.support_email)
    val intent =
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
        }
    runCatching { startActivity(intent) }
}

@Composable
fun AuthLegalFooter(
    modifier: Modifier = Modifier,
    mutedColor: androidx.compose.ui.graphics.Color,
    linkColor: androidx.compose.ui.graphics.Color,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.auth_terms_line1),
            modifier = Modifier.fillMaxWidth(),
            color = mutedColor,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.auth_terms_link),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.openUrl(context.getString(R.string.terms_url))
                    }
                    .padding(top = 1.dp),
            color = linkColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            textDecoration = TextDecoration.Underline,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.auth_privacy_link),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.openUrl(context.getString(R.string.privacy_policy_url))
                    }
                    .padding(top = 1.dp),
            color = linkColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            textDecoration = TextDecoration.Underline,
            textAlign = TextAlign.Center,
        )
    }
}


