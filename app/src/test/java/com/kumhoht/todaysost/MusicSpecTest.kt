package com.kumhoht.todaysost

import com.kumhoht.todaysost.core.model.BarMode
import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.core.model.TrackRules
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicSpecTest {

    @Test
    fun 장르별_BPM은_기획_확정값과_같다() {
        assertEquals(128, Genre.US_POP.bpm)
        assertEquals(72, Genre.POP_BALLAD.bpm)
        assertEquals(112, Genre.CHILDREN.bpm)
        assertEquals(134, Genre.TROT.bpm)
        assertEquals(140, Genre.DANCE.bpm)
    }

    @Test
    fun 마디_길이는_기본_40초_데모_5초() {
        assertEquals(40, BarMode.NORMAL.secondsPerBar)
        assertEquals(5, BarMode.DEMO.secondsPerBar)
    }

    @Test
    fun 화면_시안_30마디_미국팝은_약_56초() {
        // 30마디 × 4박 × 60 / 128 = 56.25초 (시안 표기 56초)
        assertEquals(56.25, TrackRules.songSeconds(30, Genre.US_POP), 1e-9)
    }
}
