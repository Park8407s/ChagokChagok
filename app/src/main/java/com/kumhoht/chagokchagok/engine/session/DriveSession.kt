package com.kumhoht.chagokchagok.engine.session

import com.kumhoht.chagokchagok.core.model.BarMode
import kotlin.math.max

/**
 * drive_session.py 포팅: 1초 간격 주행 기록 → 곡 마디.
 *
 * - 1마디 = 주행 '시간' 기준 (정차도 쉬는 마디로 남는다). 기본 40초, 데모 5초.
 * - 앞에 인트로 2마디, 뒤에 아웃트로 2마디가 붙는다 (주행 시간과 무관).
 * - 마지막 자투리 구간은 마디 길이의 절반 이상이면 한 마디로 친다.
 *
 * 예) 17분(1020초) 기본 모드: 1020/40 = 25.5 → 자투리 20초도 한 마디 → 주행 26마디 + 인트로·아웃트로 4 = 30마디.
 *     미국 팝(128 BPM) 30마디 × 4박 = 56.25초. (화면 시안의 "17분 운전 · 30마디 → 56초")
 */

enum class Gear { P, D, R, N }

/** 1초 간격 주행 기록 한 줄 */
data class DriveSample(
    val t: Int,
    val speed: Double,
    val gear: Gear,
    val brake: Boolean = false,
    /** 'L' / 'R' / null */
    val turn: Char? = null,
    /** 제한속도 km/h (모르면 null) */
    val limit: Int? = null,
)

enum class BarEvent { INTRO, OUTRO, HARD_ACCEL, HARD_BRAKE, SMOOTH_STOP }

/** 곡의 1마디 */
data class Bar(
    /** 마디 평균 속도 km/h (정차 마디는 0) */
    val speed: Int,
    val ev: BarEvent? = null,
    /** 깜빡이 'L' / 'R' */
    val sig: Char? = null,
    /** 과속 마디 */
    val over: Boolean = false,
)

object DriveSession {
    /** 급가속·급제동 기준 (km/h/s) */
    const val HARD_ACCEL = 10
    const val HARD_BRAKE = 14

    data class Result(val bars: List<Bar>, val tripSeconds: Int)

    fun segment(log: List<DriveSample>, mode: BarMode = BarMode.NORMAL): Result {
        val bar = mode.secondsPerBar
        // 운행 시작 = 기어 P 를 벗어난 첫 시점, 종료 = 그 뒤 기어 P + 차속 0
        val start = log.indexOfFirst { it.gear != Gear.P }
        require(start >= 0) { "기어 P 를 벗어난 기록이 없습니다" }
        val end = (start + 1 until log.size).firstOrNull { log[it].gear == Gear.P && log[it].speed == 0.0 } ?: (log.size - 1)
        val trip = log.subList(start, end + 1)

        val bars = mutableListOf(Bar(0, BarEvent.INTRO), Bar(0, BarEvent.INTRO))
        var prevSpeed = trip[0].speed
        var w0 = 0
        while (w0 < trip.size) {
            val win = trip.subList(w0, minOf(w0 + bar, trip.size))
            if (win.size < bar / 2.0 && w0 > 0) break // 마지막 자투리는 앞 마디에 합침
            val speeds = win.map { it.speed }
            val stopped = speeds.count { it == 0.0 } >= win.size * 0.6
            // Python round() 는 반올림이 아니라 '짝수 쪽 반올림'(banker's rounding)이라 rint 로 맞춘다
            val avg = if (stopped) 0 else Math.rint(speeds.sum() / speeds.size).toInt()

            var p = prevSpeed
            val deltas = speeds.map { v -> (v - p).also { p = v } }
            val ev = when {
                deltas.any { it <= -HARD_BRAKE } -> BarEvent.HARD_BRAKE
                deltas.any { it >= HARD_ACCEL } -> BarEvent.HARD_ACCEL
                !stopped && speeds.last() == 0.0 -> BarEvent.SMOOTH_STOP
                else -> null
            }
            prevSpeed = speeds.last()

            val turns = win.mapNotNull { it.turn }
            // 원본: max(set(turns), key=turns.count) — 동률이면 Python 실행마다 달라질 수 있어, 여기서는 나중에 켠 쪽을 고른다
            val sig = if (turns.size >= 2) {
                val counts = turns.groupingBy { it }.eachCount()
                val top = counts.values.max()
                turns.last { counts[it] == top }
            } else null
            val lim = win.filter { (it.limit ?: 0) != 0 }.map { it.speed > it.limit!! }
            val over = lim.isNotEmpty() && lim.count { it } >= lim.size * 0.3

            bars += Bar(avg, ev, sig, over)
            w0 += bar
        }
        bars += Bar(0, BarEvent.OUTRO)
        bars += Bar(0, BarEvent.OUTRO)
        return Result(bars, trip.size)
    }

    /** drive_session.py demo_log(): Virtual Console 약 3분 조작을 가정한 기록 */
    fun demoLog(): List<DriveSample> {
        val plan = listOf( // (구간 길이 초, 목표 속도, 방식)
            Triple(5, 0, "hold"), Triple(15, 40, "ramp"), Triple(15, 40, "hold"), Triple(10, 0, "ramp"),
            Triple(15, 0, "hold"), Triple(20, 90, "ramp"), Triple(30, 95, "hold"), Triple(2, 60, "jump"),
            Triple(10, 80, "ramp"), Triple(20, 80, "hold"), Triple(15, 30, "ramp"), Triple(10, 30, "hold"),
            Triple(10, 0, "ramp"),
        )
        val log = mutableListOf<DriveSample>()
        var t = 0
        var v = 0.0
        log += DriveSample(t++, 0.0, Gear.P, brake = true)
        for ((dur, target, how) in plan) {
            val step = (target - v) / dur
            repeat(dur) {
                v = if (how != "hold") v + step else v
                log += DriveSample(t++, max(0.0, Math.rint(v)), Gear.D, brake = step < 0)
            }
            v = target.toDouble()
        }
        log += DriveSample(t, 0.0, Gear.P, brake = true)
        return log
    }
}
