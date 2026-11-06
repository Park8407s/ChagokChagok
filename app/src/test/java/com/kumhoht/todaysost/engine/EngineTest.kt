package com.kumhoht.todaysost.engine

import com.kumhoht.todaysost.core.model.BarMode
import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.core.model.TrackRules
import com.kumhoht.todaysost.engine.composer.Composer
import com.kumhoht.todaysost.engine.composer.PyRandom
import com.kumhoht.todaysost.engine.composer.Scenarios
import com.kumhoht.todaysost.engine.session.DriveSample
import com.kumhoht.todaysost.engine.session.DriveSession
import com.kumhoht.todaysost.engine.session.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Python 없이도 확인할 수 있는 엔진 성질 */
class EngineTest {

    @Test
    fun 파이썬_난수와_같다() {
        // CPython: random.Random(1).random(), random.Random(42).random() 의 잘 알려진 첫 값
        assertEquals(0.13436424411240122, PyRandom(1).random(), 0.0)
        assertEquals(0.6394267984578837, PyRandom(42).random(), 0.0)
    }

    private fun drive(minutes: Int): List<DriveSample> {
        val s = minutes * 60
        return listOf(DriveSample(0, 0.0, Gear.P)) +
            (1..s).map { DriveSample(it, 50.0, Gear.D) } +
            DriveSample(s + 1, 0.0, Gear.P)
    }

    @Test
    fun 시안_17분_운전은_30마디_56초() {
        val r = DriveSession.segment(drive(17), BarMode.NORMAL)
        // 주행 1020초 + 도착 시점 1초 → 26마디 (25.5 마디 중 자투리 20초 이상은 한 마디) + 인트로·아웃트로 4
        assertEquals(30, r.bars.size)
        assertEquals(56.25, TrackRules.songSeconds(r.bars.size, Genre.US_POP), 1e-9)
    }

    @Test
    fun 데모모드는_5초가_1마디() {
        val r = DriveSession.segment(drive(3), BarMode.DEMO)
        assertEquals(36 + 4, r.bars.size) // 181초 → 36마디 + 자투리 1초(2.5초 미만이라 버림) + 인트로·아웃트로 4
    }

    @Test
    fun 다섯_장르_모두_시연_시나리오로_곡이_나온다() {
        for (g in Genre.entries) for (sc in listOf(Scenarios.SMOOTH, Scenarios.ROUGH)) {
            val score = Composer.compose(g, sc)
            assertEquals(30, score.barCount)
            assertEquals(TrackRules.songSeconds(30, g), score.durationSec, 1e-9)
            assertTrue("$g 음이 너무 적음: ${score.noteCount}", score.noteCount > 300)
            assertTrue(score.tracks.all { tr -> tr.notes.all { it.pitch in 0..127 && it.velocity in 1..127 && it.end > it.start } })
        }
    }
}
