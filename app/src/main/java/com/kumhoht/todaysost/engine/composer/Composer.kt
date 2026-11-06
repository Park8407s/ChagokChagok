package com.kumhoht.todaysost.engine.composer

import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.engine.session.Bar
import com.kumhoht.todaysost.engine.session.BarEvent

/** 작곡 엔진 입구: 마디 목록 + 장르 → 악보 */
object Composer {
    fun compose(genre: Genre, bars: List<Bar>): Score = when (genre) {
        Genre.US_POP -> ComposerV3("uspop").build(bars)
        Genre.POP_BALLAD -> ComposerV3("ballad").build(bars)
        Genre.TROT -> ComposerV3("trot").build(bars)
        Genre.DANCE -> ComposerV3("dance").build(bars)
        Genre.CHILDREN -> KidsComposerV2.build(bars)
    }
}

/**
 * drive_music.py 의 시연용 주행 시나리오 (1마디 = 실제 주행 약 40초, 인트로·아웃트로 포함 30마디).
 * 같은 출근길 15.0km · 17분을 A 는 부드럽게, B 는 급하게 운전한 경우.
 */
object Scenarios {
    private fun seg(speed: Int, ev: BarEvent? = null, sig: Char? = null, over: Boolean = false) = Bar(speed, ev, sig, over)
    private val I = BarEvent.INTRO
    private val O = BarEvent.OUTRO
    private val ACC = BarEvent.HARD_ACCEL
    private val BRK = BarEvent.HARD_BRAKE
    private val STOP = BarEvent.SMOOTH_STOP

    val SMOOTH: List<Bar> = listOf(
        seg(0, I), seg(0, I),
        seg(20), seg(25), seg(40), seg(45), seg(30, STOP), seg(0),
        seg(25, sig = 'R'), seg(45), seg(60), seg(70),
        seg(85), seg(90), seg(92), seg(95), seg(93), seg(90), seg(88), seg(85),
        seg(70, sig = 'L'), seg(50), seg(30, STOP), seg(0),
        seg(30), seg(40), seg(35), seg(20, STOP),
        seg(0, O), seg(0, O),
    )

    val ROUGH: List<Bar> = listOf(
        seg(0, I), seg(0, I),
        seg(35, ACC), seg(30), seg(55), seg(60), seg(40, BRK), seg(0),
        seg(40, ACC, sig = 'R'), seg(55), seg(80, ACC), seg(85),
        seg(100), seg(112, over = true), seg(118, over = true), seg(90, BRK),
        seg(115, ACC, over = true), seg(120, over = true), seg(110, over = true), seg(100),
        seg(80, sig = 'L'), seg(60, BRK), seg(35, BRK), seg(0),
        seg(45, ACC), seg(50), seg(40), seg(30, BRK),
        seg(0, O), seg(0, O),
    )
}
