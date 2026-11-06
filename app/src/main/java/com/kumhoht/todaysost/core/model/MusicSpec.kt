package com.kumhoht.todaysost.core.model

/**
 * 기획에서 확정된 값 (인수인계 문서 2장). 바꾸지 않는다.
 * 마디 분할의 정확한 계산(정차 처리, 반올림 등)은 M2 에서 원본 drive_session.py 를 포팅하며 맞춘다.
 */

/** 장르 5종과 BPM */
enum class Genre(val label: String, val bpm: Int) {
    US_POP("미국 팝", 128),
    POP_BALLAD("팝 발라드", 72),
    CHILDREN("동요", 112),
    TROT("트로트", 134),
    DANCE("댄스", 140),
}

/** 마디 모드: 주행 시간 몇 초가 1마디가 되는지 */
enum class BarMode(val label: String, val secondsPerBar: Int) {
    /** 기본 모드: 주행 40초 = 1마디 */
    NORMAL("기본 모드", 40),
    /** 데모 모드: 주행 5초 = 1마디 (PT·심사용) */
    DEMO("데모 모드", 5),
}

object TrackRules {
    /**
     * 이보다 짧은 운행은 트랙을 만들지 않는다. 화면 문구·로그·테스트가 모두 이 값을 쓴다.
     * ⚠ 테스트 버전 값 30초. 기획 확정값은 120초(2분) → 출품 전에 120 으로 되돌릴 것.
     */
    const val MIN_DRIVE_SECONDS: Int = 30
    const val MIN_DRIVE_MILLIS: Long = MIN_DRIVE_SECONDS * 1000L

    /** 4/4박자 기준 마디 수에 해당하는 곡 길이(초). */
    fun songSeconds(bars: Int, genre: Genre, beatsPerBar: Int = 4): Double =
        bars * beatsPerBar * 60.0 / genre.bpm
}
