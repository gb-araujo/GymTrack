package app.gabrielaraujo.gymtrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gabrielaraujo.gymtrack.ui.theme.TC

/**
 * Gráfico de linha minimalista desenhado em Canvas (substitui o recharts do web).
 * @param baseline valor na base do gráfico (ex.: um pouco abaixo do menor peso, para a variação aparecer).
 */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    color: Color = TC.Indigo500,
    baseline: Float = 0f,
) {
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height).padding(vertical = 8.dp)) {
            if (values.isEmpty()) return@Canvas
            val minV = baseline
            val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(minV + 1f)
            val w = size.width
            val h = size.height
            val stepX = if (values.size > 1) w / (values.size - 1) else 0f

            // grid horizontal
            val gridColor = TC.Slate800
            val lines = 4
            repeat(lines + 1) { i ->
                val y = h * i / lines
                drawLine(
                    gridColor,
                    Offset(0f, y),
                    Offset(w, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }

            fun pointFor(i: Int): Offset {
                val x = if (values.size > 1) stepX * i else w / 2
                val norm = (values[i] - minV) / (maxV - minV)
                val y = h - norm * h
                return Offset(x, y)
            }

            val path = Path()
            values.indices.forEach { i ->
                val p = pointFor(i)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, color, style = Stroke(width = 5f))
            values.indices.forEach { i ->
                val p = pointFor(i)
                drawCircle(color, radius = 7f, center = p)
            }
        }
    }
}
