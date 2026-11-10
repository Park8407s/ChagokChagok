package com.kumhoht.chagokchagok.ui.model

import com.kumhoht.chagokchagok.core.model.Genre

/**
 * 화면에 그리는 데이터. 지금은 시안의 예시 값(SampleData)을 쓰고,
 * 작곡 엔진(M2)과 저장소(Room)가 붙으면 실제 주행 기록에서 만든다.
 */

/** 타임라인 막대 하나 = 1마디 */
data class TimelineBar(
    /** 막대 높이 0..1 (곡의 세기) */
    val level: Float,
    val kind: BarKind = BarKind.PLAY,
    /** 막대 위에 붙는 사건 표시 */
    val marker: EventKind? = null,
)

enum class BarKind {
    /** 출발 전·도착 후 (회색 짧은 막대) */
    IDLE,
    /** 정차 = 쉼표 (회색 납작한 막대) */
    REST,
    /** 연주 (장르 색, 세기에 따라 진하기) */
    PLAY,
    /** 과속 구간 (노란색) */
    SPEEDING,
}

/** 주행 사건. 타임라인 표시와 곡 해설에 쓴다. */
enum class EventKind { TURN_RIGHT, TURN_LEFT, HARD_ACCEL, HARD_BRAKE, SPEEDING, HIGHWAY, STOP, PARKED }

data class TimelineLabel(val barIndex: Int, val text: String)

data class TrackNote(val icon: EventKind, val text: String)

data class TrackUi(
    val id: Long,
    /** 그날 몇 번째 트랙인지 */
    val number: Int,
    /** 예: "11월 4일 출근길" */
    val title: String,
    val genre: Genre,
    val distanceKm: Double,
    val driveMinutes: Int,
    val bars: Int,
    val seconds: Int,
    val timeline: List<TimelineBar>,
    val labels: List<TimelineLabel>,
    val notes: List<TrackNote>,
    val endingTitle: String,
    val endingBody: String,
    val hardAccel: Int = 0,
    val hardBrake: Int = 0,
    val speedingSections: Int = 0,
) {
    val numberText get() = "Track %02d".format(number)
    /** 앨범 커버의 노란 점 개수 = 거친 운전 사건 수 */
    val roughEvents get() = hardAccel + hardBrake + speedingSections
}

data class DayAlbumUi(
    val dateTitle: String,          // "11월 4일"
    val genre: Genre,               // 커버 색 (그날 가장 많이 고른 장르)
    val trackTitles: List<String>,
    val coverSeed: Long,
    val roughEvents: Int,
    /** 저장된 트랙 id (trackTitles 와 같은 순서). 시안 예시 데이터는 비어 있다 */
    val trackIds: List<Long> = emptyList(),
)

data class MonthSummaryUi(
    val month: Int,
    val trackCount: Int,
    val topGenre: Genre,
    val cleanTracks: Int,
    val longestTitle: String,
    val longestSeconds: Int,
)

/** 재생 버튼 상태: 멈춤 / 곡 만드는 중 / 재생 중 (소리가 안 들려도 버튼만 보고 알 수 있게) */
enum class PlayState { IDLE, LOADING, PLAYING }

/** 1분 40초 / 56초 */
fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return when {
        m == 0 -> "${s}초"
        s == 0 -> "${m}분"
        else -> "${m}분 ${s}초"
    }
}
