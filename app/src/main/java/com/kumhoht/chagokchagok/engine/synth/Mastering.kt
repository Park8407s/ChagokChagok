package com.kumhoht.chagokchagok.engine.synth

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 마스터링 (인터리브 스테레오 float, 제자리 처리).
 *
 * 작곡 엔진 v3 make_final.py render() 의 ffmpeg 후처리를 따른다:
 *   1. 에코      aecho=0.8:0.6:60:0.12
 *   2. 컴프레서  acompressor threshold=-18dB ratio=3 attack=10ms release=150ms
 *   3. 음량 맞춤 통합 라우드니스 -14 LUFS (loudnorm I=-14, ITU-R BS.1770 K-가중·게이팅)
 *   4. 리미터    최대 -1.5 dBFS (loudnorm TP=-1.5. 뒤의 alimiter 0.89 ≈ -1.0dBFS 보다 낮아 함께 만족)
 *   5. 끝 무음 제거: 곡 끝 1.2초 전부터 1.5초 페이드아웃, 곡 끝 + 0.3초에서 자름 ([END_PAD_SEC])
 */
object Mastering {
    const val TARGET_LUFS = -14.0
    const val CEILING_DB = -1.5
    /** 곡 끝(마지막 마디 끝) 뒤로 남기는 길이. 렌더도 여기까지만 한다 */
    const val END_PAD_SEC = 0.3
    const val FADE_BEFORE_END_SEC = 1.2
    const val FADE_SEC = 1.5

    data class Report(val inputLufs: Double, val outputLufs: Double, val peakDb: Double)

    fun process(x: FloatArray, sampleRate: Int, songSeconds: Double): Report {
        echo(x, sampleRate)
        compress(x, sampleRate)
        val before = integratedLoudness(x, sampleRate)
        if (before.isFinite()) gain(x, 10.0.pow((TARGET_LUFS - before) / 20))
        limit(x, sampleRate, 10.0.pow(CEILING_DB / 20).toFloat())
        fadeOut(x, sampleRate, songSeconds - FADE_BEFORE_END_SEC, FADE_SEC)
        val peak = x.maxOf { abs(it) }.toDouble()
        return Report(before, integratedLoudness(x, sampleRate), 20 * log10(max(peak, 1e-9)))
    }

    /** ffmpeg aecho: out = (in*0.8 + in[-60ms]*0.12) * 0.6 */
    private fun echo(x: FloatArray, sr: Int) {
        val d = (0.060 * sr).toInt() * 2
        val src = x.copyOf()
        for (i in x.indices) {
            val delayed = if (i >= d) src[i - d] else 0f
            x[i] = (src[i] * 0.8f + delayed * 0.12f) * 0.6f
        }
    }

    /** 컴프레서 (두 채널 공통 게인, RMS 검출, 어택 10ms·릴리즈 150ms) */
    private fun compress(x: FloatArray, sr: Int, thresholdDb: Double = -18.0, ratio: Double = 3.0) {
        val att = exp(-1.0 / (0.010 * sr))
        val rel = exp(-1.0 / (0.150 * sr))
        var env = 0.0
        for (i in 0 until x.size / 2) {
            val l = x[2 * i].toDouble(); val r = x[2 * i + 1].toDouble()
            val p = (l * l + r * r) / 2
            env = if (p > env) att * env + (1 - att) * p else rel * env + (1 - rel) * p
            val levelDb = 10 * log10(max(env, 1e-12))
            val over = levelDb - thresholdDb
            if (over > 0) {
                val g = 10.0.pow(-over * (1 - 1 / ratio) / 20).toFloat()
                x[2 * i] *= g; x[2 * i + 1] *= g
            }
        }
    }

    private fun gain(x: FloatArray, g: Double) { val f = g.toFloat(); for (i in x.indices) x[i] *= f }

    /** 미리보기(5ms) 리미터: 피크가 오기 전에 게인을 내리고 80ms 에 걸쳐 돌아온다 */
    private fun limit(x: FloatArray, sr: Int, ceiling: Float) {
        val n = x.size / 2
        val look = max(1, (0.005 * sr).toInt())
        val need = FloatArray(n) { i -> val p = max(abs(x[2 * i]), abs(x[2 * i + 1])); if (p > ceiling) ceiling / p else 1f }
        // 앞으로 look 프레임 안의 최소 게인 (단조 덱으로 구간 최소)
        val ahead = FloatArray(n)
        val dq = ArrayDeque<Int>()
        for (j in n - 1 downTo 0) {
            while (dq.isNotEmpty() && need[dq.last()] >= need[j]) dq.removeLast()
            dq.addLast(j)
            while (dq.first() > j + look) dq.removeFirst()
            ahead[j] = need[dq.first()]
        }
        val rel = exp(-1.0 / (0.080 * sr)).toFloat()
        var g = 1f
        for (i in 0 until n) {
            g = if (ahead[i] < g) ahead[i] else ahead[i] + (g - ahead[i]) * rel
            g = min(g, ahead[i])
            x[2 * i] *= g; x[2 * i + 1] *= g
        }
    }

    private fun fadeOut(x: FloatArray, sr: Int, startSec: Double, lenSec: Double) {
        val s = max(0, (startSec * sr).toInt())
        val len = (lenSec * sr).toInt()
        for (i in s until x.size / 2) {
            val g = (1f - (i - s).toFloat() / len).coerceIn(0f, 1f)
            x[2 * i] *= g; x[2 * i + 1] *= g
        }
    }

    /** ITU-R BS.1770-4 통합 라우드니스 (LUFS). 400ms 블록, 75% 겹침, 절대 -70 / 상대 -10 LU 게이트 */
    fun integratedLoudness(x: FloatArray, sr: Int): Double {
        val n = x.size / 2
        // K-가중 필터 계수 (libebur128 과 같은 식, 임의 샘플레이트)
        var f0 = 1681.974450955533; val gDb = 3.999843853973347; var q = 0.7071752369554196
        var k = tan(PI * f0 / sr)
        val vh = 10.0.pow(gDb / 20); val vb = vh.pow(0.4996667741545416)
        var a0 = 1 + k / q + k * k
        val pb = doubleArrayOf((vh + vb * k / q + k * k) / a0, 2 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0)
        val pa = doubleArrayOf(2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)
        f0 = 38.13547087602444; q = 0.5003270373238773; k = tan(PI * f0 / sr)
        a0 = 1 + k / q + k * k
        val ra = doubleArrayOf(2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)

        val z = DoubleArray(n) // 두 채널 K-가중 제곱합
        for (c in 0 until 2) {
            var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0   // 1단(고역 셸빙)
            var u1 = 0.0; var u2 = 0.0; var w1 = 0.0; var w2 = 0.0   // 2단(고역 통과)
            for (i in 0 until n) {
                val xi = x[2 * i + c].toDouble()
                val y = pb[0] * xi + pb[1] * x1 + pb[2] * x2 - pa[0] * y1 - pa[1] * y2
                x2 = x1; x1 = xi; y2 = y1; y1 = y
                val w = y - 2 * u1 + u2 - ra[0] * w1 - ra[1] * w2
                u2 = u1; u1 = y; w2 = w1; w1 = w
                z[i] += w * w
            }
        }
        val block = (0.4 * sr).toInt(); val hop = (0.1 * sr).toInt()
        if (n < block) return Double.NEGATIVE_INFINITY
        val prefix = DoubleArray(n + 1).also { for (i in 0 until n) it[i + 1] = it[i] + z[i] }
        val blocks = (0..(n - block) step hop).map { (prefix[it + block] - prefix[it]) / block }
        fun lufs(ms: Double) = -0.691 + 10 * log10(ms)
        val abs = blocks.filter { lufs(it) > -70 }
        if (abs.isEmpty()) return Double.NEGATIVE_INFINITY
        val rel = lufs(abs.average()) - 10
        val gated = abs.filter { lufs(it) > rel }
        return lufs(gated.average())
    }

    @Suppress("unused") private fun rms(x: FloatArray) = sqrt(x.sumOf { (it * it).toDouble() } / x.size)
}
