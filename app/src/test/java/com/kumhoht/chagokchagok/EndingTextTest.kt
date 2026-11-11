package com.kumhoht.chagokchagok

import com.kumhoht.chagokchagok.ui.model.TrackUiFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/** 도착 화면 한 줄 안내: 조건별 분리와 우선순위 (제한속도 → 급제동 → 급가속, 동률은 급제동) */
class EndingTextTest {
    private fun body(a: Int, b: Int, o: Int) = TrackUiFactory.endingBody(a, b, o)

    @Test fun clean() = assertTrue(body(0, 0, 0).startsWith("급가속·급제동 없이"))
    @Test fun oneEvent() {
        assertTrue(body(1, 0, 0).startsWith("한 번 흔들렸지만"))
        assertTrue(body(0, 1, 0).startsWith("한 번 흔들렸지만"))
    }
    @Test fun speedingFirst() {
        assertTrue(body(0, 0, 1).startsWith("제한속도를"))
        assertTrue(body(5, 5, 6).startsWith("제한속도를"))
    }
    @Test fun brakeOverAccel() {
        assertTrue(body(1, 2, 0).startsWith("미리 속도를"))
        assertTrue(body(1, 1, 0).startsWith("미리 속도를")) // 동률은 급제동
    }
    @Test fun accelMost() = assertTrue(body(2, 0, 0).startsWith("다음엔 천천히"))
}
