package com.kumhoht.chagokchagok.engine

import com.kumhoht.chagokchagok.core.model.BarMode
import com.kumhoht.chagokchagok.core.model.Genre
import com.kumhoht.chagokchagok.engine.composer.Composer
import com.kumhoht.chagokchagok.engine.composer.Scenarios
import com.kumhoht.chagokchagok.engine.composer.Score
import com.kumhoht.chagokchagok.engine.session.Bar
import com.kumhoht.chagokchagok.engine.session.DriveSession
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 원본 Python 엔진과 Kotlin 포팅이 '같은 악보'를 내는지 비교한다.
 * 기준 데이터: app/src/test/resources/parity 폴더의 .txt 파일들 (tools/parity/dump_events.py 로 생성)
 * 음 하나하나의 트랙·프로그램·높이·세기·시작·끝(초)이 비트 단위로 같아야 통과.
 */
class ParityTest {

    private fun golden(name: String): List<String>? =
        javaClass.classLoader?.getResource("parity/$name.txt")?.let { File(it.toURI()).readLines() }

    /** 음이 있는 트랙만, 원본 순서대로 같은 줄 형식으로 만든다 */
    private fun lines(score: Score): List<String> = score.tracks.filter { it.notes.isNotEmpty() }.flatMap { tr ->
        tr.notes.map { n -> "N ${tr.name} ${tr.program} ${if (tr.isDrum) 1 else 0} ${n.pitch} ${n.velocity} ${n.start} ${n.end}" } +
            tr.bends.map { b -> "B ${tr.name} ${b.value} ${b.time}" }
    }

    private fun check(name: String, score: Score) {
        val expected = golden(name)
        assumeTrue("기준 데이터 없음: parity/$name.txt (tools/parity/dump_events.py 실행 필요)", expected != null)
        val actual = lines(score)
        val exp = expected!!.map(::normalize)
        val act = actual.map(::normalize)
        val firstDiff = exp.indices.firstOrNull { it >= act.size || exp[it] != act[it] }
        if (firstDiff != null || exp.size != act.size) {
            val i = firstDiff ?: minOf(exp.size, act.size)
            fail("$name: ${i + 1}번째 줄부터 다름 (원본 ${exp.size}줄, Kotlin ${act.size}줄)\n원본:   ${exp.getOrNull(i)}\nKotlin: ${act.getOrNull(i)}")
        }
    }

    /**
     * 시간 칸(N 줄의 start·end, B 줄의 time)은 숫자로 읽어 같은 표기로 바꾼다. 값은 비트 단위 그대로 비교.
     * (Python 은 max(0, s + j) 가 정수 0 을 돌려 '0' 으로 찍히고, '1e-05' 같은 지수 표기도 Kotlin 과 다르다)
     */
    private fun normalize(line: String): String {
        val tok = line.split(' ').toMutableList()
        val timeCols = when (tok[0]) { "N" -> listOf(6, 7); "B" -> listOf(3); else -> emptyList() }
        for (c in timeCols) tok[c] = java.lang.Double.toString(tok[c].toDouble())
        return tok.joinToString(" ")
    }

    @Test fun 팝_A() = check("v3_uspop_A", Composer.compose(Genre.POP, Scenarios.SMOOTH))
    @Test fun 팝_B() = check("v3_uspop_B", Composer.compose(Genre.POP, Scenarios.ROUGH))
    @Test fun 발라드_A() = check("v3_ballad_A", Composer.compose(Genre.BALLAD, Scenarios.SMOOTH))
    @Test fun 발라드_B() = check("v3_ballad_B", Composer.compose(Genre.BALLAD, Scenarios.ROUGH))
    @Test fun 트로트_A() = check("v3_trot_A", Composer.compose(Genre.TROT, Scenarios.SMOOTH))
    @Test fun 트로트_B() = check("v3_trot_B", Composer.compose(Genre.TROT, Scenarios.ROUGH))
    @Test fun 댄스_A() = check("v3_dance_A", Composer.compose(Genre.DANCE, Scenarios.SMOOTH))
    @Test fun 댄스_B() = check("v3_dance_B", Composer.compose(Genre.DANCE, Scenarios.ROUGH))
    @Test fun 동요_A() = check("v2_kids_A", Composer.compose(Genre.CHILDREN, Scenarios.SMOOTH))
    @Test fun 동요_B() = check("v2_kids_B", Composer.compose(Genre.CHILDREN, Scenarios.ROUGH))

    @Test fun 주행기록_데모모드_곡() {
        val bars = DriveSession.segment(DriveSession.demoLog(), BarMode.DEMO).bars
        check("v3_uspop_session_demo", Composer.compose(Genre.POP, bars))
        check("v3_trot_session_demo", Composer.compose(Genre.TROT, bars))
    }

    @Test fun 주행기록_마디나누기() {
        for ((mode, file) in listOf(BarMode.NORMAL to "session_normal", BarMode.DEMO to "session_demo")) {
            val expected = golden(file)
            assumeTrue("기준 데이터 없음: parity/$file.txt", expected != null)
            val r = DriveSession.segment(DriveSession.demoLog(), mode)
            val actual = listOf("T ${r.tripSeconds}") + r.bars.mapIndexed { i, b: Bar ->
                "S $i ${b.speed} ${b.ev?.name?.lowercase() ?: "None"} ${b.sig ?: "None"} ${if (b.over) 1 else 0}"
            }
            assertEquals(file, expected, actual)
        }
    }
}
