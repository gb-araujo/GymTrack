package app.gabrielaraujo.gymtrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gabrielaraujo.gymtrack.ui.theme.TC
import kotlin.math.abs

/** Um ponto do gráfico: posição no tempo, valor e o texto mostrado ao tocar nele. */
data class ChartPoint(val time: Long, val value: Double, val detail: String)

/**
 * Gráfico de linha com eixo vertical rotulado (mín./meio/máx.), datas no eixo horizontal e
 * consulta de valores: tocar ou arrastar seleciona o ponto mais próximo e mostra seu [ChartPoint.detail].
 * O eixo horizontal é proporcional ao tempo, então intervalos sem registro aparecem como espaço,
 * e não como pontos colados.
 *
 * Com menos de [minPoints] pontos mostra [insufficientText] em vez do gráfico.
 */
@Composable
fun ProgressChart(
    points: List<ChartPoint>,
    yAxisLabel: String,
    formatY: (Double) -> String,
    formatX: (Long) -> String,
    modifier: Modifier = Modifier,
    color: Color = TC.Indigo500,
    height: Dp = 180.dp,
    minPoints: Int = 2,
    insufficientText: String = "Dados insuficientes: são necessários pelo menos 2 registros no período.",
) {
    Column(modifier.fillMaxWidth()) {
        if (points.size < minPoints) {
            Box(
                Modifier.fillMaxWidth().height(height / 2).clip(RoundedCornerShape(12.dp)).background(TC.Slate800.copy(alpha = 0.5f)).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(insufficientText, color = TC.Slate500, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            return@Column
        }

        val sorted = remember(points) { points.sortedBy { it.time } }
        var selected by remember(points) { mutableIntStateOf(sorted.lastIndex) }
        val point = sorted[selected.coerceIn(sorted.indices)]

        val rawMin = sorted.minOf { it.value }
        val rawMax = sorted.maxOf { it.value }
        // Margem de 10% para a linha não encostar nas bordas; valores iguais ganham uma faixa mínima.
        val span = (rawMax - rawMin).takeIf { it > 0 } ?: maxOf(abs(rawMax) * 0.1, 1.0)
        val minY = rawMin - span * 0.1
        val maxY = rawMax + span * 0.1
        val minX = sorted.first().time
        val maxX = sorted.last().time

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(yAxisLabel, color = TC.Slate500, fontSize = 11.sp)
            Column(horizontalAlignment = Alignment.End) {
                Text(point.detail, color = TC.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(formatX(point.time), color = TC.Slate500, fontSize = 11.sp)
            }
        }

        Row(Modifier.fillMaxWidth().height(height).padding(top = 8.dp)) {
            Column(Modifier.width(44.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(maxY, (maxY + minY) / 2, minY).forEach {
                    Text(formatY(it), color = TC.Slate500, fontSize = 10.sp, maxLines = 1)
                }
            }
            Canvas(
                Modifier.weight(1f).fillMaxHeight()
                    .pointerInput(sorted) {
                        fun pick(x: Float) {
                            selected = sorted.indices.minBy { i -> abs(xFor(sorted[i].time, minX, maxX, size.width.toFloat()) - x) }
                        }
                        detectTapGestures { pick(it.x) }
                    }
                    .pointerInput(sorted) {
                        detectHorizontalDragGestures { change, _ ->
                            val x = change.position.x
                            selected = sorted.indices.minBy { i -> abs(xFor(sorted[i].time, minX, maxX, size.width.toFloat()) - x) }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                fun yFor(v: Double) = (h - (v - minY) / (maxY - minY) * h).toFloat()

                repeat(3) { i ->
                    val y = h * i / 2
                    drawLine(TC.Slate800, Offset(0f, y), Offset(w, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }

                val offsets = sorted.map { Offset(xFor(it.time, minX, maxX, w), yFor(it.value)) }
                val path = Path()
                offsets.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                drawPath(path, color, style = Stroke(width = 4f))

                val sel = offsets[selected.coerceIn(offsets.indices)]
                drawLine(TC.Slate600, Offset(sel.x, 0f), Offset(sel.x, h), strokeWidth = 1.5f)
                offsets.forEach { drawCircle(color, radius = 6f, center = it) }
                drawCircle(TC.White, radius = 9f, center = sel)
                drawCircle(color, radius = 6f, center = sel)
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatX(minX), color = TC.Slate500, fontSize = 10.sp)
            if (maxX != minX) Text(formatX(maxX), color = TC.Slate500, fontSize = 10.sp)
        }
    }
}

private fun xFor(time: Long, minX: Long, maxX: Long, width: Float): Float =
    if (maxX == minX) width / 2 else ((time - minX).toDouble() / (maxX - minX) * width).toFloat()

/** Uma barra: rótulo do eixo horizontal, valor e o texto mostrado ao tocar. */
data class BarItem(val label: String, val value: Double, val detail: String)

/**
 * Gráfico de barras com valor máximo rotulado e consulta por toque.
 * Rótulos do eixo horizontal são mostrados a cada [labelEvery] barras para não se sobreporem.
 */
@Composable
fun BarChart(
    bars: List<BarItem>,
    yAxisLabel: String,
    formatY: (Double) -> String,
    modifier: Modifier = Modifier,
    color: Color = TC.Indigo500,
    height: Dp = 140.dp,
    labelEvery: Int = 1,
) {
    if (bars.isEmpty()) return
    var selected by remember(bars) { mutableIntStateOf(bars.lastIndex) }
    val maxV = bars.maxOf { it.value }.takeIf { it > 0 } ?: 1.0

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(yAxisLabel, color = TC.Slate500, fontSize = 11.sp)
            Text(bars[selected.coerceIn(bars.indices)].detail, color = TC.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().height(height).padding(top = 8.dp)) {
            Column(Modifier.width(36.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(formatY(maxV), color = TC.Slate500, fontSize = 10.sp, maxLines = 1)
                Text(formatY(0.0), color = TC.Slate500, fontSize = 10.sp, maxLines = 1)
            }
            Canvas(
                Modifier.weight(1f).fillMaxHeight().pointerInput(bars) {
                    detectTapGestures { offset ->
                        selected = (offset.x / (size.width.toFloat() / bars.size)).toInt().coerceIn(bars.indices)
                    }
                },
            ) {
                val slot = size.width / bars.size
                val barW = slot * 0.6f
                drawLine(TC.Slate700, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
                bars.forEachIndexed { i, bar ->
                    val h = (bar.value / maxV * size.height).toFloat()
                    val left = slot * i + (slot - barW) / 2
                    drawRoundRect(
                        if (i == selected) color else color.copy(alpha = 0.45f),
                        topLeft = Offset(left, size.height - h),
                        size = androidx.compose.ui.geometry.Size(barW, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 36.dp, top = 4.dp)) {
            bars.forEachIndexed { i, bar ->
                Text(
                    if (i % labelEvery == 0 || i == bars.lastIndex) bar.label else "",
                    color = TC.Slate500, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
