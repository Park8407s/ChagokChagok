package com.kumhoht.todaysost.ui.model

import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.core.model.TrackRules
import kotlin.math.roundToInt

/**
 * 시안(reference/figma)의 예시 데이터.
 * 타임라인 막대 높이는 Figma 레이어 높이(px, 바닥선 기준 최대 288)를 그대로 옮겼다.
 * 출발 15.0km · 17분 · 30마디는 시안 표기를 따른다 (40초 = 1마디 기준과의 차이는 M2 에서 drive_session.py 와 대조).
 */
object SampleData {

    private const val CHART_MAX = 288f

    /** A 부드러운 운전 (그림 3): 30마디 */
    private val smoothHeights = intArrayOf(
        14, 14, 42, 53, 84, 95, 63, 7, 53, 95, 126, 148, 179, 190, 194,
        200, 196, 190, 185, 179, 148, 105, 63, 7, 63, 84, 74, 42, 14, 14,
    )

    /** B 급한 운전 (그림 4): 30마디 */
    private val roughHeights = intArrayOf(
        14, 14, 74, 63, 116, 126, 84, 7, 84, 116, 169, 179, 211, 236, 249,
        190, 242, 253, 232, 211, 169, 126, 74, 7, 95, 105, 84, 63, 14, 14,
    )

    private fun bars(heights: IntArray, markers: Map<Int, EventKind>, speeding: Set<Int> = emptySet()) =
        heights.mapIndexed { i, h ->
            val kind = when {
                i <= 1 || i >= heights.size - 2 -> BarKind.IDLE
                h <= 7 -> BarKind.REST
                i in speeding -> BarKind.SPEEDING
                else -> BarKind.PLAY
            }
            TimelineBar(h / CHART_MAX, kind, markers[i])
        }

    private val labels = listOf(
        TimelineLabel(0, "출발"), TimelineLabel(7, "신호 대기"), TimelineLabel(12, "고속 구간"),
        TimelineLabel(23, "신호 대기"), TimelineLabel(28, "도착"),
    )

    private fun seconds(genre: Genre, bars: Int) = TrackRules.songSeconds(bars, genre).roundToInt()

    fun smoothTrack(genre: Genre = Genre.US_POP) = TrackUi(
        id = 101, number = 1, title = "11월 4일 출근길", genre = genre,
        distanceKm = 15.0, driveMinutes = 17, bars = 30, seconds = seconds(genre, 30),
        timeline = bars(smoothHeights, mapOf(8 to EventKind.TURN_RIGHT, 20 to EventKind.TURN_LEFT)),
        labels = labels,
        notes = listOf(
            TrackNote(EventKind.HIGHWAY, "고속 구간에서 코러스와 스트링이 더해졌어요."),
            TrackNote(EventKind.STOP, "신호 대기 2번은 피아노만 남는 쉬어가는 부분이 됐어요."),
            TrackNote(EventKind.TURN_RIGHT, "깜빡이 두 번이 반짝이는 소리로 들어갔어요."),
        ),
        endingTitle = "밝게 끝났어요",
        endingBody = "급가속·급제동 없이 도착해서\n곡이 깔끔하게 마무리됐어요.",
    )

    fun roughTrack(genre: Genre = Genre.US_POP) = TrackUi(
        id = 102, number = 1, title = "11월 4일 출근길", genre = genre,
        distanceKm = 15.0, driveMinutes = 17, bars = 30, seconds = seconds(genre, 30),
        timeline = bars(
            roughHeights,
            mapOf(
                2 to EventKind.HARD_ACCEL, 6 to EventKind.HARD_BRAKE, 8 to EventKind.HARD_ACCEL,
                10 to EventKind.HARD_ACCEL, 15 to EventKind.HARD_BRAKE, 16 to EventKind.HARD_ACCEL,
                20 to EventKind.TURN_LEFT, 21 to EventKind.HARD_BRAKE, 22 to EventKind.HARD_BRAKE,
                24 to EventKind.HARD_ACCEL, 27 to EventKind.HARD_BRAKE,
            ),
            speeding = setOf(13, 14, 16, 17, 18),
        ),
        labels = labels,
        notes = listOf(
            TrackNote(EventKind.HARD_BRAKE, "급제동 5번, 그때마다 음악이 잠깐 끊겼어요."),
            TrackNote(EventKind.SPEEDING, "제한속도를 넘은 구간에 경고 소리가 깔렸어요."),
            TrackNote(EventKind.HARD_ACCEL, "급가속 5번이 치솟는 소리로 남았어요."),
        ),
        endingTitle = "조금 아쉽게 끝났어요",
        endingBody = "다음엔 천천히 출발해 보세요.\n곡이 더 부드럽게 끝나요.",
        hardAccel = 5, hardBrake = 5, speedingSections = 5,
    )

    /** 홈의 '어제의 OST' (그림 2) */
    val yesterday = TrackUi(
        id = 201, number = 2, title = "11월 3일 퇴근길", genre = Genre.POP_BALLAD,
        distanceKm = 12.0, driveMinutes = 21, bars = 30, seconds = 100,
        timeline = emptyList(), labels = emptyList(), notes = emptyList(), endingTitle = "", endingBody = "",
    )
    const val yesterdayTrackCount = 2

    /** 내 OST 앨범 (그림 5) */
    val days = listOf(
        DayAlbumUi("11월 4일", Genre.US_POP, listOf("출근길", "마트 가는 길", "퇴근길"), 1104, 6),
        DayAlbumUi("11월 3일", Genre.POP_BALLAD, listOf("출근길", "퇴근길"), 1103, 0),
        DayAlbumUi("11월 2일", Genre.TROT, listOf("할머니 댁 가는 길", "돌아오는 길"), 1102, 0),
        DayAlbumUi("11월 1일", Genre.DANCE, listOf("출근길", "퇴근길", "야간 드라이브"), 1101, 7),
        DayAlbumUi("10월 31일", Genre.US_POP, listOf("출근길", "퇴근길"), 1031, 0),
        DayAlbumUi("10월 30일", Genre.CHILDREN, listOf("키즈카페 가는 길", "돌아오는 길"), 1030, 0),
        DayAlbumUi("10월 29일", Genre.POP_BALLAD, listOf("출근길"), 1029, 9),
        DayAlbumUi("10월 28일", Genre.US_POP, listOf("출근길", "병원 가는 길", "퇴근길"), 1028, 0),
    )

    val month = MonthSummaryUi(
        month = 11, trackCount = 21, topGenre = Genre.US_POP, cleanTracks = 19,
        longestTitle = "할머니 댁 가는 길", longestSeconds = 370,
    )
}
