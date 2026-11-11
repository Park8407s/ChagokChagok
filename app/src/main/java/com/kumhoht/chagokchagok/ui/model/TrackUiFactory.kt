package com.kumhoht.chagokchagok.ui.model

import com.kumhoht.chagokchagok.core.model.Genre
import com.kumhoht.chagokchagok.engine.session.Bar
import com.kumhoht.chagokchagok.engine.session.BarEvent
import com.kumhoht.chagokchagok.engine.composer.Composer
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.pow

/**
 * 엔진이 쓴 마디 목록으로 화면용 TrackUi 를 만든다 (타임라인·곡 해설·마지막 화음 문구).
 * 문구는 시안의 표현을 따르되, 실제 장르 규칙(drive_music_v3)에서 그 상황에 나는 악기 이름을 쓴다.
 */
object TrackUiFactory {

    /**
     * 막대 높이 = 그 마디에서 실제로 연주되는 소리의 양 (작곡 엔진이 만든 악보를 다시 계산).
     * 음마다 세기(velocity) × 길이를 마디별로 더하고, 곡에서 가장 큰 마디를 1 로 맞춘다.
     * 그래서 같은 운전이라도 장르마다 (악기 수·리듬이 달라) 모양이 다르고, A/B 운전에 따라서도 달라진다.
     * 같은 장르·같은 마디면 항상 같은 악보라 결과를 기억해 둔다 (화면이 다시 그려질 때마다 작곡하지 않게).
     */
    private val levelCache = object : LinkedHashMap<Pair<Genre, List<Bar>>, FloatArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Genre, List<Bar>>, FloatArray>) = size > 24
    }

    @Synchronized
    private fun scoreLevels(genre: Genre, bars: List<Bar>): FloatArray = levelCache.getOrPut(genre to bars) {
        val score = runCatching { Composer.compose(genre, bars) }.getOrNull() ?: return@getOrPut FloatArray(bars.size) { 0.3f }
        val barLen = 240.0 / score.bpm
        val energy = DoubleArray(bars.size)
        for (t in score.tracks) for (n in t.notes) {
            // 길게 끄는 음(마지막 화음 등)은 걸친 마디마다 그 시간만큼 나눠 담는다. 드럼은 짧아서 한 번 칠 때 0.15마디로 본다
            val w = if (t.isDrum) 0.6 else 1.0
            val end = if (t.isDrum) n.start + barLen * 0.15 else n.end
            var i = (n.start / barLen).toInt()
            while (i in energy.indices && i * barLen < end) {
                val overlap = min(end, (i + 1) * barLen) - max(n.start, i * barLen)
                if (overlap > 0) energy[i] += n.velocity * overlap * w
                i++
            }
        }
        // 위쪽 10% 마디를 최대로 보고(한두 마디가 튀어도 나머지가 납작해지지 않게), 1.6제곱으로 대비를 키운다
        val top = energy.sorted().let { it[(it.size * 0.9).toInt().coerceAtMost(it.size - 1)] }.takeIf { it > 0 } ?: 1.0
        FloatArray(bars.size) { i -> (energy[i] / top).pow(1.6).toFloat().coerceIn(0.05f, 1f) }
    }

    fun from(
        id: Long, number: Int, title: String, genre: Genre, bars: List<Bar>,
        seconds: Double, distanceKm: Double, driveMinutes: Int,
    ): TrackUi {
        val levels = scoreLevels(genre, bars)
        val timeline = bars.mapIndexed { i, b ->
            val kind = when {
                b.ev == BarEvent.INTRO || b.ev == BarEvent.OUTRO -> BarKind.IDLE
                b.speed == 0 -> BarKind.REST
                b.over -> BarKind.SPEEDING
                else -> BarKind.PLAY
            }
            // 인트로·아웃트로는 곡 끝 페이드아웃·잔향 구간이라 실제로 들리는 크기에 맞춰 낮게 (길게 끄는 마지막 화음이 솟지 않게)
            val lv = if (kind == BarKind.IDLE) min(levels[i], 0.3f) else levels[i]
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
                if (highway) add(TrackNote(EventKind.HIGHWAY, "고속 구간에서 ${highwayVoice(genre)} 더해지며 곡이 가장 커졌어요."))
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
            endingBody = endingBody(accel, brake, over),
            hardAccel = accel, hardBrake = brake, speedingSections = over,
        )
    }

    /**
     * 도착 화면 한 줄 안내 (기획 확정안). 마지막 화음 판정(급가속+급제동 ≤ 1, 과속 0)과 같은 기준으로 나눈다.
     * 아쉽게 끝난 이유가 여럿이면 제한속도 초과 → 급제동 → 급가속 순으로 하나만, 급제동·급가속 횟수가 같으면 급제동.
     */
    internal fun endingBody(accel: Int, brake: Int, over: Int): String = when {
        over == 0 && accel + brake == 0 -> "급가속·급제동 없이 도착해\n곡이 깔끔하게 마무리됐어요."
        over == 0 && accel + brake == 1 -> "한 번 흔들렸지만\n곡이 깔끔하게 마무리됐어요."
        over > 0 -> "제한속도를 지키면\n곡이 더 안정적으로 흘러요."
        brake >= accel -> "미리 속도를 줄여 보세요.\n음악이 끊기지 않아요."
        else -> "다음엔 천천히 출발해 보세요.\n곡이 더 부드럽게 끝나요."
    }

    /** 이름표끼리 겹치지 않게 (4마디 이내면 앞의 것만) */
    private fun dropCrowded(labels: List<TimelineLabel>): List<TimelineLabel> {
        val out = mutableListOf<TimelineLabel>()
        for (l in labels) if (out.isEmpty() || l.barIndex - out.last().barIndex >= 4) out += l
        return out
    }

    /** 속도 90 이상(에너지 4)에서 더해지는 악기 (drive_music_v3 / v2 kids) */
    private fun highwayVoice(g: Genre) = when (g) {
        Genre.POP -> "코러스가" // 팝은 고속 구간에서 코러스만 더해짐 (스트링 없음, 피그마 8-1)
        Genre.BALLAD -> "첼로와 스트링이"
        Genre.TROT -> "트럼펫이"
        Genre.DANCE -> "하이햇과 신스 리드가"
        Genre.CHILDREN -> "실로폰이"
    }

    /** 정차 마디에서 남는 악기 */
    private fun restVoice(g: Genre) = when (g) {
        Genre.POP, Genre.BALLAD -> "피아노"
        Genre.TROT -> "오르간"
        Genre.DANCE -> "신스 패드"
        Genre.CHILDREN -> "오르골"
    }
}
