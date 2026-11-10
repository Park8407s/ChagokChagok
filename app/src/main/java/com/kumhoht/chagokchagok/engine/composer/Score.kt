package com.kumhoht.chagokchagok.engine.composer

/**
 * 작곡 엔진이 만드는 악보 (원본 pretty_midi 의 Instrument·Note·PitchBend 에 해당).
 * 시간은 곡 시작부터의 초.
 */
data class ScoreNote(val pitch: Int, val velocity: Int, val start: Double, val end: Double)

data class ScoreBend(val value: Int, val time: Double)

class ScoreTrack(
    /** 원본의 악기 이름 (pad, comp, mel, ... , drums) */
    val name: String,
    /** GM 프로그램 번호 (드럼은 0) */
    val program: Int,
    val isDrum: Boolean = false,
) {
    val notes = mutableListOf<ScoreNote>()
    val bends = mutableListOf<ScoreBend>()
}

class Score(
    val bpm: Int,
    /** 원본 순서대로: 악기들(정의 순서) 다음 드럼. 음이 없는 트랙도 포함 */
    val tracks: List<ScoreTrack>,
    val barCount: Int,
) {
    /** 마디 수 × 4박 (아웃트로 2마디 포함) */
    val durationSec: Double get() = barCount * (60.0 / bpm * 4)
    val noteCount: Int get() = tracks.sumOf { it.notes.size }
}
