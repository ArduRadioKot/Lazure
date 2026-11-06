package com.rassvet.essential.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import com.rassvet.essential.ui.theme.LocalEssentialChrome
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


object EssentialLogo {
    const val VIEWPORT = 1000f
    const val OUTLINE_STROKE_VP = 50f
    const val CRACK_STROKE_VP = 37.5f

    const val THINKING_OUTLINE_STROKE_VP = 58f
    const val THINKING_CRACK_STROKE_VP = 72f

    val defaultStroke = Color(0xFFC4C4C4)

    const val OUTLINE =
        "M488.5,213.9C528.1,202.5 574,226.3 603.3,255.3C632.3,284 623.9,333.9 645.1,368.8C663,398.2 703.8,409.6 716.5,441.6C729.5,474.2 715.5,510.4 715.5,545.5C715.5,589.4 744.5,641.6 716.8,675.7C689,710 628.4,682.5 588.8,701.9C548.8,721.6 533.1,788.3 488.5,788C443.7,787.7 422,730.7 388.9,700.6C363.8,677.7 335.2,659.2 316.1,631.2C297.7,604.1 287.9,573 280.6,541.1C273.3,509.2 267.9,476.8 273.1,444.5C278.5,411.2 287.5,376.3 311.1,352.2C334.5,328.5 375.5,334 401.8,313.6C436.9,286.2 445.7,226.3 488.5,213.9Z"

    val CRACKS =
        listOf(
            "M315.9,348.6C384.9,447.4 394.9,515.2 356.1,676.2",
            "M389.9,320.2C574.8,438.5 404.8,649 540.3,747.1",
            "M606.9,280.2C484.4,390.1 633.4,532.6 478.4,564.2",
            "M719.6,672C593.4,654.3 698.6,490.7 543.6,522.3",
        )

    fun parsePath(data: String): Path = PathParser().parsePathString(data).toPath()
}

@Composable
fun EssentialLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeColor: Color = EssentialLogo.defaultStroke,
) {
    val outline = remember { EssentialLogo.parsePath(EssentialLogo.OUTLINE) }
    val cracks = remember { EssentialLogo.CRACKS.map { EssentialLogo.parsePath(it) } }

    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.minDimension / EssentialLogo.VIEWPORT
        val outlineStyle =
            Stroke(
                width = EssentialLogo.OUTLINE_STROKE_VP * scale,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        val crackStyle =
            Stroke(
                width = EssentialLogo.CRACK_STROKE_VP * scale,
                cap = StrokeCap.Butt,
                join = StrokeJoin.Round,
            )
        withTransform({
            translate(this.size.width / 2f, this.size.height / 2f)
            scale(scale, scale, pivot = Offset.Zero)
            translate(-EssentialLogo.VIEWPORT / 2f, -EssentialLogo.VIEWPORT / 2f)
        }) {
            clipPath(outline) {
                cracks.forEach { path ->
                    drawPath(path, strokeColor, style = crackStyle)
                }
            }
            drawPath(outline, strokeColor, style = outlineStyle)
        }
    }
}


@Composable
fun EssentialLogoThinkingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val outlineProgress = remember { Animatable(0f) }
    val crackProgresses = remember { EssentialLogo.CRACKS.map { Animatable(0f) } }

    val outline = remember { EssentialLogo.parsePath(EssentialLogo.OUTLINE) }
    val cracks = remember { EssentialLogo.CRACKS.map { EssentialLogo.parsePath(it) } }
    val strokeColor = LocalEssentialChrome.current.thinkingMuted

    LaunchedEffect(Unit) {
        while (isActive) {
            outlineProgress.snapTo(0f)
            crackProgresses.forEach { it.snapTo(0f) }

            outlineProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            )

            crackProgresses.forEach { progress ->
                launch {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                    )
                }
                delay(240)
            }
            delay(360)

            crackProgresses.reversed().forEach { progress ->
                launch {
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                    )
                }
                delay(180)
            }
            delay(360)

            outlineProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            )
            delay(180)
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val drawScale = this.size.minDimension / EssentialLogo.VIEWPORT
        val outlineStyle =
            Stroke(
                width = EssentialLogo.THINKING_OUTLINE_STROKE_VP * drawScale,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        val crackStyle =
            Stroke(
                width = EssentialLogo.THINKING_CRACK_STROKE_VP * drawScale,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        withTransform({
            translate(this.size.width / 2f, this.size.height / 2f)
            scale(drawScale, drawScale, pivot = Offset.Zero)
            translate(-EssentialLogo.VIEWPORT / 2f, -EssentialLogo.VIEWPORT / 2f)
        }) {
            if (outlineProgress.value < 1f) {
                drawPartialPath(outline, outlineProgress.value, strokeColor, outlineStyle)
            } else {
                clipPath(outline) {
                    cracks.forEachIndexed { index, path ->
                        drawPartialPath(
                            path,
                            crackProgresses[index].value,
                            strokeColor,
                            crackStyle,
                        )
                    }
                }
                drawPath(outline, strokeColor, style = outlineStyle)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPartialPath(
    path: Path,
    progress: Float,
    color: Color,
    style: Stroke,
) {
    if (progress <= 0f) return
    val measure = PathMeasure()
    measure.setPath(path, false)
    val length = measure.length
    if (length <= 0f) return
    val segment = Path()
    measure.getSegment(0f, length * progress.coerceIn(0f, 1f), segment, true)
    drawPath(segment, color, style = style)
}


