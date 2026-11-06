package com.kumhoht.todaysost.ui.model

import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.engine.session.Bar
import com.kumhoht.todaysost.engine.session.BarEvent
import kotlin.math.roundToInt

/**
 * 엔진이 쓴 마디 목록으로 화면용 TrackUi 를 만든다 (타임라인·곡 해설·마지막 화음 문구).
 * 문구는 시안의 표현을 따르되, 실제 장르 규칙(drive_music_v3)에서 그 상황에 나는 악기 이름을 쓴다.
 */
object TrackUiFactory {

    /** 시안 막대 높이(px)/속도 비율 ≈ 2.1, 그래프 최대 높이 288 */
    private fun level(speed: Int) = (speed * 2.1f / 288f).coerceIn(0.06f, 1f)

    fun from(
        id: Long, number: Int, title: String, genre: Genre, bars: List<Bar>,
        seconds: Double, distanceKm: Double, driveMinutes: Int,
    ): TrackUi {
        val timeline = bars.map { b ->
            val kind = when {
                b.ev == BarEvent.INTRO || b.ev == BarEvent.OUTRO -> BarKind.IDLE
                b.speed == 0 -> BarKind.REST
                b.over -> BarKind.SPEEDING
                else -> BarKind.PLAY
            }
            val lv = when (kind) { BarKind.IDLE -> 14 / 288f; BarKind.REST -> 7 / 288f; else -> level(b.speed) }
            val marker = when {
                b.ev == BarEvent.HARD_ACCEL -> EventKind.HARD_ACCEL
                b.ev == BarEvent.HARD_BRAKE -> EventKind.HARD_BRAKE
                b.sig == 'R' -> EventKind.TURN_RIGHT
                b.sig == 'L' -> EventKind.TURN_LEFT
                else -> null
            }
            TimelineBar(lv, kind, marker)
        }

        val labels = buildList {
            add(TimelineLabel(0, "출발"))
            val firstHigh = bars.indexOfFirst { it.ev != BarEvent.INTRO && it.ev != BarEvent.OUTRO && it.speed >= 85 }
            bars.forEachIndexed { i, b -> if (b.speed == 0 && b.ev != BarEvent.INTRO && b.ev != BarEvent.OUTRO) add(TimelineLabel(i, "신호 대기")) }
            if (firstHigh >= 0) add(TimelineLabel(firstHigh, "고속 구간"))
            add(TimelineLabel(bars.size - 2, "도착"))
        }.sortedBy { it.barIndex }.let(::dropCrowded)

        val accel = bars.count { it.ev == BarEvent.HARD_ACCEL }
        val brake = bars.count { it.ev == BarEvent.HARD_BRAKE }
        val over = bars.count { it.over }
        val stops = bars.count { it.speed == 0 && it.ev != BarEvent.INTRO && it.ev != BarEvent.OUTRO }
        val turns = bars.count { it.sig != null }
        val highway = bars.any { it.speed >= 90 && it.ev != BarEvent.INTRO && it.ev != BarEvent.OUTRO }
        val good = accel + brake <= 1 && over == 0 // 원본 good = hard <= 1 and overs == 0

        val notes = buildList {
            if (good) {
                if (highway) add(TrackNote(EventKind.HIGHWAY, "고속 구간에서 ${highwayVoice(genre)} 더해졌어요."))
                if (stops > 0) add(TrackNote(EventKind.STOP, "신호 대기 ${stops}번은 ${restVoice(genre)}만 남는 쉬어가는 부분이 됐어요."))
                if (turns > 0) add(TrackNote(EventKind.TURN_RIGHT, "깜빡이 ${turns}번이 반짝이는 소리로 들어갔어요."))
            } else {
                if (brake > 0) add(TrackNote(EventKind.HARD_BRAKE, "급제동 ${brake}번, 그때마다 음악이 잠깐 끊겼어요."))
                if (over > 0) add(TrackNote(EventKind.SPEEDING, "제한속도를 넘은 구간에 경고 소리가 깔렸어요."))
                if (accel > 0) add(TrackNote(EventKind.HARD_ACCEL, "급가속 ${accel}번이 치솟는 소리로 남았어요."))
            }
        }.take(3)

        return TrackUi(
            id = id, number = number, title = title, genre = genre,
            distanceKm = distanceKm, driveMinutes = driveMinutes, bars = bars.size, seconds = seconds.roundToInt(),
            timeline = timeline, labels = labels, notes = notes,
            endingTitle = if (good) "밝게 끝났어요" else "조금 아쉽게 끝났어요",
            endingBody = if (good) "급가속·급제동 없이 도착해서\n곡이 깔끔하게 마무리됐어요." else "다음엔 천천히 출발해 보세요.\n곡이 더 부드럽게 끝나요.",
            hardAccel = accel, hardBrake = brake, speedingSections = over,
        )
    }

    /** 이름표끼리 겹치지 않게 (4마디 이내면 앞의 것만) */
    private fun dropCrowded(labels: List<TimelineLabel>): List<TimelineLabel> {
        val out = mutableListOf<TimelineLabel>()
        for (l in labels) if (out.isEmpty() || l.barIndex - out.last().barIndex >= 4) out += l
        return out
    }

    /** 속도 90 이상(에너지 4)에서 더해지는 악기 (drive_music_v3 / v2 kids) */
    private fun highwayVoice(g: Genre) = when (g) {
        Genre.US_POP -> "코러스와 크래시가"
        Genre.POP_BALLAD -> "첼로와 스트링이"
        Genre.TROT -> "트럼펫이"
        Genre.DANCE -> "하이햇과 신스 리드가"
        Genre.CHILDREN -> "실로폰이"
    }

    /** 정차 마디에서 남는 악기 */
    private fun restVoice(g: Genre) = when (g) {
        Genre.US_POP, Genre.POP_BALLAD -> "피아노"
        Genre.TROT -> "오르간"
        Genre.DANCE -> "신스 패드"
        Genre.CHILDREN -> "오르골"
    }
}
