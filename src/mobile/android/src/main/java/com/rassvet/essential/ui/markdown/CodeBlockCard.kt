package com.rassvet.essential.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R
import com.rassvet.essential.ui.theme.EssentialBrand
import com.rassvet.essential.ui.theme.LocalEssentialChrome

@Composable
fun CodeBlockCard(
    code: String,
    language: String,
    baseColor: Color,
    modifier: Modifier = Modifier,
    surfaceColor: Color? = null,
    borderColor: Color? = null,
) {
    val chrome = LocalEssentialChrome.current
    val resolvedSurface = surfaceColor ?: chrome.sheetContainer
    val resolvedBorder = borderColor ?: chrome.sheetDivider
    val context = LocalContext.current
    val colors = remember(baseColor) { defaultCodeHighlightColors(baseColor) }
    val highlighted = remember(code, language, colors) {
        highlightCode(code, language, colors)
    }
    val label = remember(language) { displayLanguageLabel(language) }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(shape)
            .border(1.dp, resolvedBorder, shape)
            .background(resolvedSurface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(chrome.sheetSelectedBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = EssentialBrand.copy(alpha = 0.95f),
                style = TextStyle(fontSize = 12.sp, letterSpacing = 0.4.sp),
            )
            IconButton(
                onClick = { copyCodeToClipboard(context, code) },
                modifier = Modifier.padding(0.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_code),
                    tint = baseColor.copy(alpha = 0.75f),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = highlighted,
                style = TextStyle(
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                ),
            )
        }
    }
}

private fun copyCodeToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
    Toast.makeText(context, context.getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
}


