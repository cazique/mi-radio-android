package com.miradio.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Onda decorativa que sugiere actividad de audio. ExoPlayer no expone la
 * amplitud real del stream sin un AudioProcessor a medida, así que se anima
 * de forma pseudoaleatoria mientras se reproduce y se aplana al pausar,
 * igual que hacen la mayoría de reproductores de radio.
 */
@Composable
fun Waveform(isActive: Boolean, modifier: Modifier = Modifier, barCount: Int = 28) {
    val heights = remember(barCount) { List(barCount) { Random.nextFloat() } }
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (isActive) (2 * Math.PI).toFloat() else 0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    // Sin esto, al pausar/reanudar la onda saltaba en seco entre "plana" y
    // "moviéndose" de golpe. activeFactor interpola entre ambos estados
    // (0 = plana, 1 = onda completa) en vez de conmutar de golpe.
    val activeFactor by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "waveformActive",
    )

    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val barWidth = size.width / (barCount * 2f)
        heights.forEachIndexed { index, base ->
            val flatAmplitude = 0.12f
            val activeAmplitude = 0.25f + 0.75f * abs(sin(phase + base * 6.28f))
            val amplitude = flatAmplitude + (activeAmplitude - flatAmplitude) * activeFactor
            val barHeight = size.height * amplitude
            val x = barWidth * (index * 2 + 1)
            drawLine(
                color = color,
                start = Offset(x, size.height / 2f - barHeight / 2f),
                end = Offset(x, size.height / 2f + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
