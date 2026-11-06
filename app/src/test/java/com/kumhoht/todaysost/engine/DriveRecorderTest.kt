package com.kumhoht.todaysost.engine

import com.kumhoht.todaysost.core.model.BarMode
import com.kumhoht.todaysost.engine.session.DriveRecorder
import com.kumhoht.todaysost.engine.session.DriveSession
import com.kumhoht.todaysost.engine.session.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveRecorderTest {

    /** P 대기 → D 로 seconds 초 주행 → P 정차 */
    private fun drive(seconds: Int, speed: Double = 40.0): DriveRecorder.Trip? {
        val r = DriveRecorder()
        var t = 0L
        repeat(5) { assertNull(r.tick(t++ * 1000, 0.0, Gear.P, true, null, null)) } // 주차 중에는 기록 안 함
        assertFalse(r.recording)
        repeat(seconds) { assertNull(r.tick(t++ * 1000, speed, Gear.D, false, null, null)) }
        return r.tick(t * 1000, 0.0, Gear.P, true, null, null)
    }

    @Test
    fun 도착하면_운행이_끝난다() {
        val trip = drive(180)!!
        assertEquals(181, trip.seconds) // D 180초 + 도착 1초
        assertFalse(trip.tooShort)
        assertEquals(2.0, trip.distanceKm, 1e-9) // 40km/h × 180초
        // 기록은 segment() 에 그대로 넣을 수 있다
        assertEquals(36 + 4, DriveSession.segment(trip.log, BarMode.DEMO).bars.size)
    }

    @Test
    fun 최소_운행_시간_미만은_트랙을_만들지_않는다() {
        val min = com.kumhoht.todaysost.core.model.TrackRules.MIN_DRIVE_SECONDS // 테스트 버전 30초, 기획 120초
        // drive(n) = D n초 + 도착 1초 → 운행 n+1초
        assertTrue(drive(min - 2)!!.tooShort)
        assertFalse(drive(min - 1)!!.tooShort)
    }
}
