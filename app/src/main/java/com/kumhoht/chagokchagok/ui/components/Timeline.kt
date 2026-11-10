package com.kumhoht.chagokchagok.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kumhoht.chagokchagok.ui.model.BarKind
import com.kumhoht.chagokchagok.ui.model.EventKind
import com.kumhoht.chagokchagok.ui.model.TimelineBar
import com.kumhoht.chagokchagok.ui.model.TimelineLabel
import com.kumhoht.chagokchagok.ui.theme.Ost
import com.kumhoht.chagokchagok.ui.theme.OstType

/**
 * 곡 해설 타임라인: 1마디 = 막대 1개.
 * 높이 = 곡의 세기, 색 = 장르 색(약할수록 어둡게), 과속 = 노랑, 정차·출발·도착 = 회색.
 * 막대 위에 깜빡이(흰 화살표)·급가속·급제동(노랑) 표시를 붙인다.
 *
 * @param chartHeight 바닥선까지의 높이 (시안: 큰 차트 288, 미니 138)
 * @param barRatio 막대 폭 / 막대 간격 (시안: 큰 차트 25/32, 미니 7/14)
 */
@Composable
fun TimelineChart(
    bars: List<TimelineBar>,
    color: Color,
    modifier: Modifier = Modifier,
    labels: List<TimelineLabel> = emptyList(),
    chartHeight: Dp = 288.dp,
    barRatio: Float = 0.78f,
    markerSize: Dp = 16.dp,
    labelStyle: TextStyle = OstType.Caption,
) {
    val painters = mapOf(
        EventKind.TURN_RIGHT to painterResource(EventKind.TURN_RIGHT.iconRes),
        EventKind.TURN_LEFT to painterResource(EventKind.TURN_LEFT.iconRes),
        EventKind.HARD_ACCEL to painterResource(EventKind.HARD_ACCEL.iconRes),
        EventKind.HARD_BRAKE to painterResource(EventKind.HARD_BRAKE.iconRes),
    )
    val maxPlay = bars.filter { it.kind == BarKind.PLAY || it.kind == BarKind.SPEEDING }.maxOfOrNull { it.level } ?: 1f

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pitch = maxWidth / bars.size.coerceAtLeast(1)
        Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
            val p = size.width / bars.size
            val w = p * barRatio
            val base = size.height
            val radius = CornerRadius(minOf(w * 0.18f, 4.dp.toPx()))
            drawLine(Ost.Line, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
            bars.forEachIndexed { i, bar ->
                val h = (bar.level * base).coerceAtLeast(3.dp.toPx())
                val x = i * p + (p - w) / 2
                val gap = if (bar.kind == BarKind.PLAY || bar.kind == BarKind.SPEEDING) 0f else 2.dp.toPx()
                val fill = when (bar.kind) {
                    BarKind.IDLE, BarKind.REST -> Ost.Idle
                    BarKind.SPEEDING -> Ost.Yellow
                    BarKind.PLAY -> lerp(Ost.Card, color, 0.5f + 0.5f * (bar.level / maxPlay))
                }
                drawRoundRect(fill, Offset(x, base - h - gap), Size(w, h), radius)

                val marker = bar.marker ?: return@forEachIndexed
                val painter: Painter = painters[marker] ?: return@forEachIndexed
                val m = markerSize.toPx()
                val tint = if (marker == EventKind.TURN_LEFT || marker == EventKind.TURN_RIGHT) Ost.Text else Ost.Yellow
                translate(left = i * p + (p - m) / 2, top = base - h - m - 8.dp.toPx()) {
                    with(painter) { draw(Size(m, m), colorFilter = ColorFilter.tint(tint)) }
                }
            }
        }
        labels.forEach { label ->
            Box(Modifier.offset(x = pitch * label.barIndex, y = chartHeight + 6.dp)) {
                Text(label.text, style = labelStyle)
            }
        }
    }
}
