package com.kumhoht.chagokchagok.engine.session

import com.kumhoht.chagokchagok.core.model.TrackRules
import kotlin.math.roundToInt

/**
 * 주행 기록기: 1초마다 [tick] 을 부르면 운행 하나를 모아서 끝났을 때 돌려준다.
 *
 * - 시작: 기어가 P 를 벗어난 첫 순간 (drive_session.segment 와 같은 기준)
 * - 끝:  그 뒤 기어 P + 차속 0 (= 도착)
 * - 최소 운행 시간(TrackRules.MIN_DRIVE_SECONDS, 30초) 미만은 트랙을 만들지 않는다 ([Trip.tooShort])
 * 주행 중에는 화면도 소리도 없이 기록만 한다 (호출하는 쪽 책임).
 */
class DriveRecorder {

    data class Trip(
        /** segment() 에 그대로 넣을 수 있는 기록 (앞에 P 1줄 포함) */
        val log: List<DriveSample>,
        val startedAt: Long,
        val endedAt: Long,
        val distanceKm: Double,
    ) {
        /** 기어 P 를 벗어나 있던 시간(초) */
        val seconds: Int get() = log.size - 1
        val minutes: Int get() = (seconds / 60.0).roundToInt()
        val tooShort: Boolean get() = seconds * 1000L < TrackRules.MIN_DRIVE_MILLIS
    }

    private val samples = mutableListOf<DriveSample>()
    private var startedAt = 0L
    private var distanceKm = 0.0

    val recording: Boolean get() = samples.isNotEmpty()
    val recordedSeconds: Int get() = (samples.size - 1).coerceAtLeast(0)

    /**
     * @return 운행이 끝났으면 그 기록, 아니면 null
     */
    fun tick(nowMs: Long, speedKmh: Double, gear: Gear, brake: Boolean, turn: Char?, limit: Int?): Trip? {
        val speed = Math.rint(speedKmh) // 원본 기록은 km/h 정수
        if (!recording) {
            if (gear == Gear.P) return null
            startedAt = nowMs
            distanceKm = 0.0
            samples += DriveSample(0, 0.0, Gear.P, brake = true) // 출발 직전 P (segment 의 시작 기준점)
        }
        samples += DriveSample(samples.size, speed, gear, brake, turn, limit)
        distanceKm += speedKmh / 3600.0
        if (gear == Gear.P && speed == 0.0) {
            val trip = Trip(samples.toList(), startedAt, nowMs, distanceKm)
            samples.clear()
            return trip
        }
        return null
    }

    fun reset() = samples.clear()
}
