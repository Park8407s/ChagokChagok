package com.kumhoht.todaysost.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kumhoht.todaysost.ui.theme.Ost
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 앨범 커버 (자동 생성). 시안의 동심원 레코드 모양을 코드로 그린다.
 * - 같은 [seed] 면 항상 같은 그림
 * - 색은 장르 색
 * - [roughEvents](급가속·급제동·과속 수)만큼 노란 점이 흩어지고, 많을수록 원이 더 잘게 끊긴다
 * 크기 비율은 시안(483px 커버)의 레이어 크기에서 가져왔다.
 */
@Composable
fun AlbumCover(
    seed: Long,
    color: Color,
    roughEvents: Int,
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
) {
    val spec = remember(seed, roughEvents) { CoverSpec.build(seed, roughEvents) }
    Canvas(modifier) {
        val s = min(size.width, size.height)
        val c = center
        drawRoundRect(Ost.CoverBg, cornerRadius = CornerRadius(corner.toPx()))

        fun at(r: Float, deg: Float) = Offset(
            c.x + r * s * cos(Math.toRadians(deg.toDouble())).toFloat(),
            c.y + r * s * sin(Math.toRadians(deg.toDouble())).toFloat(),
        )

        // 가장 바깥 점선 원
        for (i in 0 until 140) drawCircle(color.copy(alpha = 0.28f), radius = s * 0.0022f, center = at(0.448f, i * 360f / 140))

        // 동심원 호
        for (ring in spec.rings) {
            val r = ring.radius * s
            val stroke = Stroke(width = ring.width * s, cap = StrokeCap.Butt)
            for (seg in ring.segments) {
                drawArc(
                    color = color.copy(alpha = ring.alpha),
                    startAngle = seg.first, sweepAngle = seg.second, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = stroke,
                )
            }
        }

        // 흰 점 2개, 노란 점(거친 운전 사건)
        spec.whiteDots.forEach { (r, a) -> drawCircle(Color.White, radius = s * 0.0105f, center = at(r, a)) }
        spec.yellowDots.forEach { (r, a) -> drawCircle(Ost.Yellow, radius = s * 0.0155f, center = at(r, a)) }

        // 가운데 라벨과 구멍
        drawCircle(color, radius = s * 0.08f, center = c)
        drawCircle(Ost.CoverBg, radius = s * 0.0145f, center = c)
    }
}

private class Ring(val radius: Float, val width: Float, val alpha: Float, val segments: List<Pair<Float, Float>>)

private class CoverSpec(
    val rings: List<Ring>,
    val whiteDots: List<Pair<Float, Float>>,
    val yellowDots: List<Pair<Float, Float>>,
) {
    companion object {
        /** (반지름, 두께, 투명도, 채움 비율) — 시안 레이어 크기 / 483 */
        private val RINGS = listOf(
            floatArrayOf(0.430f, 0.004f, 0.75f, 0.55f),
            floatArrayOf(0.400f, 0.004f, 0.60f, 0.45f),
            // 굵은 띠: 두께가 간격보다 커서 서로 겹쳐 꽉 찬 도넛처럼 보인다 (겹친 곳의 투명도 차이가 결무늬)
            floatArrayOf(0.366f, 0.022f, 0.85f, 0.90f),
            floatArrayOf(0.346f, 0.022f, 0.95f, 0.97f),
            floatArrayOf(0.326f, 0.022f, 1.00f, 1.00f),
            floatArrayOf(0.306f, 0.020f, 0.90f, 0.85f),
            floatArrayOf(0.286f, 0.012f, 0.55f, 0.70f),
            floatArrayOf(0.266f, 0.006f, 0.45f, 0.60f),
            floatArrayOf(0.243f, 0.008f, 0.60f, 0.75f),
            floatArrayOf(0.218f, 0.012f, 0.70f, 0.80f),
            floatArrayOf(0.198f, 0.006f, 0.50f, 0.90f),
            floatArrayOf(0.165f, 0.003f, 0.40f, 0.50f),
            floatArrayOf(0.131f, 0.003f, 0.50f, 1.00f),
            floatArrayOf(0.120f, 0.002f, 0.35f, 0.60f),
        )

        fun build(seed: Long, roughEvents: Int): CoverSpec {
            val rnd = Random(seed)
            val choppy = (roughEvents / 4).coerceAtMost(3)
            val rings = RINGS.map { (r, w, a, fill) ->
                // 채움 비율만큼의 각도를 1~(3+choppy) 조각으로 나눠 무작위 위치에 놓는다
                val pieces = 1 + rnd.nextInt(2 + choppy)
                val total = 360f * fill
                val gap = (360f - total) / pieces
                var angle = rnd.nextFloat() * 360f
                val segs = (0 until pieces).map {
                    val sweep = total / pieces * (0.7f + rnd.nextFloat() * 0.6f).coerceAtMost(1f)
                    (angle to sweep).also { angle += total / pieces + gap }
                }
                Ring(r, w, a, segs)
            }
            val white = listOf(0.20f to rnd.nextFloat() * 360f, 0.41f to rnd.nextFloat() * 360f)
            val yellow = (0 until roughEvents.coerceAtMost(14)).map {
                (0.12f + rnd.nextFloat() * 0.34f) to rnd.nextFloat() * 360f
            }
            return CoverSpec(rings, white, yellow)
        }
    }
}
