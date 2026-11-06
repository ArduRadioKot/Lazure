package com.rassvet.essential.ui.markdown

import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ru.noties.jlatexmath.JLatexMathDrawable


private const val LATEX_RENDER_SCALE = 1.1f

private fun buildLatexDrawable(latex: String, color: Color, textSizePx: Float): JLatexMathDrawable? =
    try {
        JLatexMathDrawable.Builder(latex.trim())
            .textSize(textSizePx * LATEX_RENDER_SCALE)
            .color(color.toArgb())
            .build()
    } catch (_: Exception) {
        null
    }

@Composable
private fun rememberLatexDrawable(
    latex: String,
    textColor: Color,
    textSizeSp: Float,
): JLatexMathDrawable? {
    val density = LocalDensity.current
    val textSizePx = with(density) { textSizeSp.sp.toPx() }
    return remember(latex, textColor, textSizePx) {
        buildLatexDrawable(latex, textColor, textSizePx)
    }
}

@Composable
private fun LatexImageView(
    drawable: JLatexMathDrawable,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart,
) {
    val density = LocalDensity.current
    val widthDp: Dp =
        with(density) {
            (drawable.intrinsicWidth.coerceAtLeast(1) / density.density).dp
        }
    val heightDp: Dp =
        with(density) {
            (drawable.intrinsicHeight.coerceAtLeast(1) / density.density).dp
        }
    Box(modifier = modifier, contentAlignment = alignment) {
        AndroidView(
            modifier = Modifier
                .width(widthDp)
                .height(heightDp),
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    adjustViewBounds = false
                    layoutParams =
                        ViewGroup.LayoutParams(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                        )
                    setImageDrawable(drawable)
                }
            },
            update = { view ->
                view.setImageDrawable(drawable)
                view.layoutParams.width = drawable.intrinsicWidth.coerceAtLeast(1)
                view.layoutParams.height = drawable.intrinsicHeight.coerceAtLeast(1)
            },
        )
    }
}

@Composable
fun LatexBlockView(
    latex: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 17f,
    compact: Boolean = false,
) {
    val drawable = rememberLatexDrawable(latex, textColor, textSizeSp)
    if (drawable == null) {
        Text(
            text = latex,
            color = textColor.copy(alpha = 0.9f),
            fontFamily = FontFamily.Monospace,
            fontSize = textSizeSp.sp,
            modifier = modifier.padding(vertical = 8.dp),
        )
        return
    }
    LatexImageView(
        drawable = drawable,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 8.dp),
        alignment = Alignment.Center,
    )
}

@Composable
fun LatexInlineChip(
    latex: String,
    textColor: Color,
    textSizeSp: Float = 18f,
) {
    val drawable = rememberLatexDrawable(latex, textColor, textSizeSp)
    if (drawable == null) {
        Text(
            text = "$$latex$",
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = textSizeSp.sp,
        )
        return
    }
    LatexImageView(
        drawable = drawable,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineMarkdownText(
    text: String,
    baseColor: Color,
    mutedColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val segments = remember(text) { parseInlineSegments(text) }
    val hasMath = segments.any { it is InlineSegment.Math }
    val inlineMathSp = (style.fontSize.value * 1.02f).coerceIn(15f, 18f)

    if (!hasMath) {
        Text(
            text = inlineAnnotatedFromSegments(segments, baseColor, mutedColor),
            style = style,
            modifier = modifier,
        )
        return
    }

    if (segments.size == 1 && segments[0] is InlineSegment.Math) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LatexInlineChip(
                latex = (segments[0] as InlineSegment.Math).latex,
                textColor = baseColor,
                textSizeSp = inlineMathSp,
            )
        }
        return
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        segments.forEach { segment ->
            when (segment) {
                is InlineSegment.Text ->
                    Text(text = segment.value, style = style, color = baseColor)
                is InlineSegment.Bold ->
                    Text(
                        text = segment.value,
                        style = style.copy(fontWeight = FontWeight.Bold),
                        color = baseColor,
                    )
                is InlineSegment.Italic ->
                    Text(
                        text = segment.value,
                        style = style.copy(fontStyle = FontStyle.Italic),
                        color = baseColor,
                    )
                is InlineSegment.Code ->
                    Text(
                        text = segment.value,
                        style = style.copy(fontFamily = FontFamily.Monospace),
                        color = baseColor,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .background(mutedColor.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                is InlineSegment.Math ->
                    LatexInlineChip(
                        latex = segment.latex,
                        textColor = baseColor,
                        textSizeSp = inlineMathSp,
                    )
            }
        }
    }
}

private fun inlineAnnotatedFromSegments(
    segments: List<InlineSegment>,
    base: Color,
    muted: Color,
): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        segments.forEach { segment ->
            when (segment) {
                is InlineSegment.Text -> append(segment.value)
                is InlineSegment.Bold ->
                    withStyle(SpanStyle(color = base, fontWeight = FontWeight.Bold)) {
                        append(segment.value)
                    }
                is InlineSegment.Italic ->
                    withStyle(SpanStyle(color = base, fontStyle = FontStyle.Italic)) {
                        append(segment.value)
                    }
                is InlineSegment.Code ->
                    withStyle(
                        SpanStyle(
                            color = base,
                            fontFamily = FontFamily.Monospace,
                            background = muted.copy(alpha = 0.25f),
                        ),
                    ) {
                        append(segment.value)
                    }
                is InlineSegment.Math -> append("$${segment.latex}$")
            }
        }
    }


