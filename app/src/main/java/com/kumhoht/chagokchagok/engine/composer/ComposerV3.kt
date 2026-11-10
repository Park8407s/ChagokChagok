package com.kumhoht.chagokchagok.engine.composer

import com.kumhoht.chagokchagok.engine.session.Bar
import com.kumhoht.chagokchagok.engine.session.BarEvent
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * drive_music_v3.py build() 포팅 (미국 팝·발라드·트로트·댄스).
 *
 * 같은 마디 목록을 넣으면 Python 과 똑같은 음(높이·세기·시작·끝)이 나와야 한다.
 * 그래서 계산 순서(부동소수점 덧셈·곱셈 순서)와 난수 호출 순서를 원본 그대로 옮겼다.
 * 원본 줄과 대조하기 쉽도록 변수 이름도 원본을 따른다 (BEAT, BAR, E, ci, cut ...).
 */
internal class ComposerV3(private val genre: String) {

    fun build(drive: List<Bar>): Score {
        val g = V3_GENRES.getValue(genre)
        val BEAT = 60.0 / g.bpm
        val BAR = BEAT * 4
        val ins = g.inst.mapValues { (name, prog) -> ScoreTrack(name, prog) }
        val drums = ScoreTrack("drums", 0, isDrum = true)
        val human = PyRandom(3)

        fun note(k: ScoreTrack, p: Int, s: Double, d: Double, v: Double) {
            val j = human.uniform(-0.008, 0.008)
            k.notes += ScoreNote(pitch = p, velocity = max(1, min(127, v.toInt())), start = max(0.0, s + j), end = s + max(d, .05))
        }
        fun note(k: String, p: Int, s: Double, d: Double, v: Double) = note(ins.getValue(k), p, s, d, v)
        fun note(k: String, p: Int, s: Double, d: Double, v: Int) = note(ins.getValue(k), p, s, d, v.toDouble())
        fun dr(p: Int, s: Double, v: Int) = note(drums, p, s, .1, v.toDouble())

        val hard = drive.count { it.ev == BarEvent.HARD_ACCEL || it.ev == BarEvent.HARD_BRAKE }
        val overs = drive.count { it.over }
        val good = hard <= 1 && overs == 0
        val rnd = PyRandom(21)
        val cache = HashMap<Triple<Int, String, Char?>, List<Triple<Double, Double, Int>>>()
        val (lo, hi) = g.melRange
        val pool = (lo..hi).filter { Math.floorMod(it, 12) in (g.penta ?: g.scale) }
        var melPrev = (lo + hi) / 2
        var ci = 0
        val bends = mutableListOf<ScoreBend>() // 트로트 색소폰 비브라토·꺾기

        for ((i, b) in drive.withIndex()) {
            val t = i * BAR
            val ev = b.ev
            val E = energyOf(b)
            val nxtE = if (i + 1 < drive.size) energyOf(drive[i + 1]) else 0
            val prevE = if (i > 0) energyOf(drive[i - 1]) else 0

            // ================= 인트로 =================
            if (ev == BarEvent.INTRO) {
                val first = i == 0
                when (genre) {
                    "uspop" -> {
                        val c = g.chords.getValue(if (first) "C" else "G").first
                        for (s in doubleArrayOf(0.0, 1.5, 3.0)) for (n in c) note("comp", n, t + s * BEAT, BEAT * .9, 70)
                        for (bt in intArrayOf(1, 3)) dr(39, t + bt * BEAT, 60 + 20 * bi(!first))
                        if (!first) for (k in 0 until 8) dr(38, t + BEAT * 2 + k * BEAT / 4, 50 + k * 7)
                    }
                    "ballad" -> {
                        val c = g.chords.getValue(if (first) "C" else "G").first
                        val arp = listOf(c[0] - 12, c[0], c[1], c[2], c[1] + 12, c[2], c[1], c[0])
                        for ((k, n) in arp.withIndex()) note("comp", n, t + k * BEAT / 2, BEAT * 1.5, 55)
                        note("mel", if (first) 76 else 74, t + BEAT * 2, BEAT * 2, 60)
                    }
                    "trot" -> {
                        if (first) {
                            for ((k, n) in listOf(69, 72, 76, 81, 79, 76).withIndex()) note("mel", n, t + k * BEAT * .5, BEAT * .45, 90)
                            for (n in listOf(57, 60, 64)) note("acc", n + 12, t + BEAT * 3, BEAT, 100)
                            dr(49, t + BEAT * 3, 90)
                        } else {
                            for (k in 0 until 8) dr(if (k > 3) 38 else 47, t + k * BEAT / 2, 75 + k * 5)
                            for (s in doubleArrayOf(3.0, 3.5)) for (n in listOf(64, 68, 71)) note("acc", n, t + s * BEAT, BEAT * .4, 105)
                            note("bass", 40, t + BEAT * 3.5, BEAT * .5, 95)
                        }
                    }
                    else -> {
                        val c = g.chords.getValue("Am").first
                        for (n in c) note("pad", n, t, BAR, 45 + 20 * bi(!first))
                        val step = if (first) BEAT / 2 else BEAT / 4
                        var k = 0
                        while (k * step < BAR - 1e-6) { dr(38, t + k * step, 40 + k * (if (first) 4 else 3)); k++ }
                        if (!first) for ((k2, n) in (69 until 93 step 2).withIndex()) note("lead", n, t + BEAT * 2 + k2 * BEAT / 6, BEAT / 6, 50 + k2 * 4)
                    }
                }
                continue
            }

            // ================= 아웃트로 =================
            if (ev == BarEvent.OUTRO) {
                if (i == drive.size - 2) {
                    val end = if (good) g.goodEnd else g.badEnd
                    for (n in end) note("pad", n, t, BAR * 2, 62)
                    val se = end.sorted()
                    if (good) {
                        val ups = se.drop(2) + (se.last() + 5)
                        for ((k, n) in ups.withIndex()) note("comp", n + (if (genre == "uspop" || genre == "ballad") 12 else 0), t + k * BEAT / 2, BAR * 2 - k * BEAT / 2, 62)
                        if (genre == "trot") {
                            for ((k, n) in listOf(76, 72, 69).withIndex()) note("mel", n, t + k * BEAT * .5, BEAT * (if (k < 2) .5 else 4.0), 90)
                            for (n in listOf(57, 60, 64, 69)) note("acc", n + 12, t + BEAT * 1.5, BEAT * 2, 100)
                            dr(49, t + BEAT * 1.5, 100); dr(36, t + BEAT * 1.5, 110)
                        }
                        if (genre == "dance") { dr(49, t, 110); dr(36, t, 120); note("acc", 69, t, BEAT, 100) }
                        if (genre == "uspop") {
                            dr(49, t, 90); dr(36, t, 100)
                            for ((k, n) in listOf(72, 74, 76).withIndex()) note("mel", n, t + k * BEAT * .5, BEAT * (if (k < 2) .5 else 4.0), 75)
                        }
                        if (genre == "ballad") { note("mel", 72, t + BEAT, BAR * 1.5, 60); note("cello", 36, t, BAR * 2, 55) }
                    } else {
                        for ((k, n) in se.drop(1).withIndex()) note("comp", n, t + k * BEAT / 2, BEAT * 1.5, 60)
                        note("comp", se[1] + 1, t + BAR, BAR, 45)
                    }
                }
                continue
            }

            val stopped = E == 0
            var chord: List<Int>
            val root: Int
            if (stopped) {
                val c = g.chords.getValue(g.stop[(i / 8) % 2]); chord = c.first; root = c.second
            } else {
                val c = g.chords.getValue(g.prog[ci % g.prog.size]); ci += 1
                chord = c.first; root = c.second
                if (ev == BarEvent.HARD_ACCEL || ev == BarEvent.HARD_BRAKE || b.over) chord = tense(chord, root)
            }
            val ch = chord
            val cut = if (ev != BarEvent.HARD_BRAKE) BAR else BEAT * 2.5
            val pcs = ch.map { Math.floorMod(it, 12) }
            fun within(s: Double) = s < cut - 1e-6

            // ================= 장르별 반주 =================
            when (genre) {
                "uspop" -> {
                    for (n in ch) note("pad", n, t, cut, if (!stopped) 38 else 45)
                    if (stopped) {
                        for ((k, n) in (ch + (ch[1] + 12)).withIndex()) note("comp", n + 12, t + k * BEAT, BEAT * 2, 45)
                    } else {
                        // 피아노 트레실로(3-3-2) 스탭 = 미국 팝 시그니처 리듬
                        val stabs = if (E < 3) doubleArrayOf(0.0, 1.5, 3.0) else doubleArrayOf(0.0, .75, 1.5, 2.5, 3.0, 3.5)
                        for (s in stabs) if (within(s * BEAT)) for (n in ch) note("comp", n, t + s * BEAT, BEAT * .45, 62 + 4 * E)
                        if (E >= 2) for (k in 0 until 8) if (within(k * BEAT / 2)) note("gtr", ch[0] - 12, t + k * BEAT / 2, BEAT * .3, 58 + 8 * bi(k % 2 == 0))
                        if (E >= 2) { // 펌핑 신스베이스
                            val pat = if (E >= 3) listOf(0.0 to 0, .5 to 0, 1.5 to 12, 2.0 to 0, 2.5 to 0, 3.0 to 7, 3.5 to 12)
                            else listOf(0.0 to 0, 1.5 to 0, 2.5 to 0, 3.0 to 0)
                            for ((s, off) in pat) if (within(s * BEAT)) note("bass", root + off, t + s * BEAT, BEAT * .4, 85)
                        }
                        // 드럼: 킥 1, 2&, 3 / 스네어+클랩 2,4
                        if (E >= 1) for (bt in intArrayOf(1, 3)) if (within(bt * BEAT)) dr(39, t + bt * BEAT, 70 + 5 * E)
                        if (E >= 2) {
                            for (s in doubleArrayOf(0.0, 1.5, 2.0)) if (within(s * BEAT)) dr(36, t + s * BEAT, 95)
                            for (bt in intArrayOf(1, 3)) if (within(bt * BEAT)) dr(38, t + bt * BEAT, 85)
                            val sub = if (E >= 3 || b.over) 4 else 2
                            for (k in 0 until 4 * sub) if (within(k * BEAT / sub)) dr(if (b.over && k % 4 == 2) 46 else 42, t + k * BEAT / sub, 40 + 18 * bi(k % sub == 0))
                        }
                        if (E >= 4 && Math.floorMod(ci - 1, 4) == 0) dr(49, t, 85)
                        if (E >= 3 && Math.floorMod(ci - 1, 4) == 3 && within(BEAT * 3)) for ((k, tm) in listOf(50, 48, 47, 45).withIndex()) dr(tm, t + BEAT * 3 + k * BEAT / 4, 80)
                        if (E >= 4) { // "우-우" 코러스 콜앤리스폰스
                            for ((k, n) in listOf(ch.last() + 12, ch[1] + 12).withIndex()) if (within((2.5 + k * .5) * BEAT)) note("mel2", n, t + (2.5 + k * .5) * BEAT, BEAT * .45, 70)
                        }
                    }
                }
                "ballad" -> {
                    for (n in ch) note("pad", n, t, cut, 42 + 4 * E)
                    val step = if (E < 3) BEAT / 2 else BEAT / 4
                    val arp = listOf(root + 12, ch[0], ch[1], ch[2], if (ch.size < 4) ch[1] + 12 else ch[3], ch[2], ch[1], ch[0])
                    var k = 0
                    while (within(k * step)) { note("comp", arp[k % arp.size], t + k * step, step * 2.5, 45 + 3 * E + 6 * bi(k % 4 == 0)); k++ }
                    if (E >= 1 && within(0.0)) note("bass", root, t, min(cut, BAR) * .95, 70)
                    if (E >= 3) {
                        dr(36, t, 70)
                        if (within(BEAT * 2.5)) dr(36, t + BEAT * 2.5, 55)
                        if (within(BEAT * 2)) dr(if (E >= 4) 38 else 37, t + BEAT * 2, 70)
                        if (E >= 4) {
                            for (bt in 0 until 4) if (within(bt * BEAT)) dr(51, t + bt * BEAT, 45)
                            if (Math.floorMod(ci - 1, 4) == 0) dr(49, t, 70)
                        }
                    }
                    if (E >= 4) note("cello", if (root > 40) root - 12 else root, t, cut, 60)
                }
                "trot" -> {
                    if (stopped || E == 1) for (n in ch) note("pad", n, t, cut, 45)
                    for (bt in 0 until 4) { // 오르간 엇박 "짝"
                        val s = (bt + .5) * BEAT
                        if (E >= 1 && within(s)) for (n in ch) note("comp", n, t + s, BEAT * .3, 55 + 5 * E)
                    }
                    if (E >= 2) for (k in 0 until 8) if (within(k * BEAT / 2) && k % 2 == 1) note("gtr", ch.last() + 12, t + k * BEAT / 2, BEAT * .2, 55)
                    if (E >= 3 && (i % 2 == 1 || E >= 4) && within(BEAT * 3.5)) { // 브라스 "빰빠"
                        for (s in doubleArrayOf(3.0, 3.5)) for (n in ch) note("acc", n + 12, t + s * BEAT, BEAT * .35, 95)
                    }
                    val r = if (root <= 45) root else root - 12
                    if (E >= 1) {
                        val pat = if (E <= 2) listOf(0.0 to 0, 2.0 to 7) else (0 until 8).map { x -> x * .5 to 12 * (x % 2) }
                        for ((s, off) in pat) if (within(s * BEAT)) note("bass", r + off, t + s * BEAT, BEAT * .45, 88)
                    }
                    if (E >= 1) {
                        for (bt in 0 until 4) {
                            val s = bt * BEAT
                            if (!within(s)) break
                            if (E >= 3 || bt == 0 || bt == 2) dr(36, t + s, 100)
                            if (E >= 2 && (bt == 1 || bt == 3)) dr(38, t + s, 85)
                            if (within(s + BEAT / 2)) dr(46, t + s + BEAT / 2, 55 + 10 * bi(E >= 3))
                        }
                        if (E >= 3) for (k in 0 until 16) if (within(k * BEAT / 4)) dr(54, t + k * BEAT / 4, 40 + 20 * bi(k % 4 == 2))
                        if (E >= 3 && Math.floorMod(ci - 1, 4) == 0) dr(49, t, 85)
                        if (E >= 3 && Math.floorMod(ci - 1, 8) == 7 && within(BEAT * 3)) for ((k, tm) in listOf(50, 48, 47, 45).withIndex()) dr(tm, t + BEAT * 3 + k * BEAT / 4, 90)
                    }
                }
                else -> { // dance
                    // 사이드체인처럼 숨쉬는 패드: 엇박마다 짧게
                    if (stopped || E == 1) {
                        for (n in ch) note("pad", n, t, cut, 50)
                    } else {
                        for (k in 0 until 4) {
                            val s = (k + .5) * BEAT
                            if (within(s)) for (n in ch) note("pad", n, t + s, BEAT * .45, 60 + 3 * E)
                        }
                    }
                    if (E >= 3) {
                        val arp = ch + listOf(ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 12)
                        var k = 0
                        while (within(k * BEAT / 4)) { note("comp", arp[k % arp.size] + 12, t + k * BEAT / 4, BEAT / 4 * .6, 38 + 3 * E); k++ }
                    }
                    if (E >= 2) for (k in 0 until 8) {
                        val s = k * BEAT / 2
                        if (within(s) && (k % 2 == 1 || E >= 4)) note("bass", root - 12 + 12 * bi(k % 4 == 3), t + s, BEAT * .4, 95)
                    }
                    if (E >= 1) {
                        for (bt in 0 until 4) {
                            val s = bt * BEAT
                            if (!within(s)) break
                            if (E >= 2) dr(36, t + s, if (E >= 3) 110 else 85)
                            if (E >= 3 && (bt == 1 || bt == 3)) { dr(39, t + s, 90); dr(38, t + s, 60) }
                            if (within(s + BEAT / 2)) dr(46, t + s + BEAT / 2, 60 + 10 * bi(E >= 3))
                        }
                        if (E >= 4 || b.over) for (k in 0 until 16) if (within(k * BEAT / 4)) dr(42, t + k * BEAT / 4, 35 + 15 * bi(k % 4 == 0))
                    }
                    // 빌드업 → 드롭
                    if (nxtE >= 3 && E < 3 && ev != BarEvent.HARD_BRAKE) {
                        for (k in 0 until 8) dr(38, t + BEAT * 2 + k * BEAT / 4, 55 + k * 8)
                        for ((k, n) in (69 until 85 step 2).withIndex()) note("lead", n, t + BEAT * 2 + k * BEAT / 4, BEAT / 4, 50 + k * 5)
                    }
                    if (E >= 3 && prevE < 3) {
                        dr(49, t, 110)
                        for (n in listOf(57, 64, 69)) note("acc", n, t, BEAT, 100)
                    }
                }
            }

            // ================= 멜로디 =================
            if (E >= 2) {
                val lvl = if (E == 2) "calm" else if (E == 3) "mid" else "high"
                val pos = Math.floorMod(ci - 1, g.prog.size)
                val key = Triple(pos, lvl, b.sig)
                if (key !in cache) {
                    val rs = g.rhythm.getValue(lvl)
                    val rhythm = rs[((ci - 1) / g.prog.size) % rs.size]
                    val contour = if (b.sig == 'R') 1 else if (b.sig == 'L') -1 else (if (pos % 4 < 2) 1 else -1)
                    val seq = mutableListOf<Triple<Double, Double, Int>>()
                    var prev = melPrev
                    for ((s, d) in rhythm) {
                        val cands = if (s == 0.0 || s == 2.0) pool.filter { Math.floorMod(it, 12) in pcs } else pool
                        val target = prev + contour * rnd.choice(STEPS)
                        val p = (cands.ifEmpty { pool }).minBy { kotlin.math.abs(it - target) }
                        seq += Triple(s, d, p); prev = p
                    }
                    cache[key] = seq
                }
                val mv = when (genre) { "uspop" -> 80; "ballad" -> 72; "trot" -> 92; else -> 76 }
                for ((s, d, p) in cache.getValue(key)) {
                    if (!within(s * BEAT)) break
                    val st = t + s * BEAT
                    val dur = d * BEAT * .95
                    if (genre == "trot") {
                        if (d >= 1.5) {
                            val up = pool.filter { it > p }.minOrNull() ?: (p + 2)
                            note("mel", up, st, BEAT * .16, mv - 8)
                            note("mel", p, st + BEAT * .16, dur - BEAT * .16, mv)
                            val v0 = st + dur * .4 // 비브라토
                            val steps = ((dur * .6) / .03).toInt()
                            for (k in 0 until steps) bends += ScoreBend((900 * sin(2 * PI * 5.5 * k * .03)).toInt(), v0 + k * .03)
                            bends += ScoreBend(0, st + dur)
                        } else {
                            bends += ScoreBend(-2048, st); bends += ScoreBend(0, st + .06)
                            note("mel", p, st, dur, mv)
                        }
                    } else {
                        note("mel", p, st, dur, mv)
                    }
                    if (E >= 4 && genre == "uspop") note("mel2", p - 12, st, dur, 60)
                    if (E >= 4 && genre == "ballad") note("mel2", p, st, dur, 50)
                    if (E >= 3 && genre == "dance") note("mel2", p - 12, st, dur, 55)
                    if (E >= 4 && genre == "trot") note("mel2", p, st, dur, 55)
                    melPrev = p
                }
            }

            // ================= 공통 운전 이벤트 =================
            if (b.sig != null) {
                var seq = ch.map { it + 24 }.toSortedSet().toList()
                if (b.sig == 'L') seq = seq.reversed()
                for (k in 0 until 8) note("spark", seq[k % seq.size], t + k * BEAT / 2, BEAT / 2, 62)
            }
            if (ev == BarEvent.HARD_ACCEL) {
                val run = (60 until 100).filter { Math.floorMod(it, 12) in g.scale }.take(16)
                for ((k, n) in run.withIndex()) note("lead", n, t + BEAT * 2 + k * BEAT / 8, BEAT / 8, 72 + k)
                for (k in 0 until 4) dr(36, t + BEAT * 3 + k * BEAT / 4, 105)
            }
            if (ev == BarEvent.HARD_BRAKE) {
                val s = t + cut
                for ((k, tm) in listOf(50, 47, 45).withIndex()) dr(tm, t + cut - BEAT * .75 + k * BEAT / 4, 85)
                dr(49, s, 112); dr(36, s, 118)
                when (genre) {
                    "trot" -> for (n in listOf(52, 53, 56)) note("acc", n, s, BEAT * .8, 105)
                    "dance" -> { note("bass", 33, s, BEAT * 1.2, 120); note("acc", 45, s, BEAT, 110) }
                    else -> for (n in listOf(36, 37, 42, 43)) note("comp", n, s, BEAT * 1.2, 100)
                }
            }
            if (b.over) {
                val base = when (genre) { "uspop" -> 79; "ballad" -> 84; "trot" -> 76; else -> 81 }
                for (k in 0 until 8) note("lead", base + (k % 2), t + k * BEAT / 2, BEAT / 2.2, 58)
            }
            if (ev == BarEvent.SMOOTH_STOP) {
                val home = g.chords.getValue(g.prog[0]).first
                for (n in home) note("comp", n + 12, t + BEAT * 3, BEAT * 2, 55)
            }
        }

        if (bends.isNotEmpty()) ins.getValue("mel").bends += bends.sortedBy { it.time } // 안정 정렬 (원본 list.sort 와 같음)
        return Score(g.bpm, ins.values.toList() + drums, drive.size)
    }

    private companion object {
        val STEPS = listOf(1, 2, 2, 3)

        fun energyOf(b: Bar): Int {
            val sp = b.speed
            if (b.ev == BarEvent.INTRO || b.ev == BarEvent.OUTRO || sp == 0) return 0
            return if (sp < 30) 1 else if (sp < 60) 2 else if (sp < 90) 3 else 4
        }
    }
}

/** Python 의 True/False 를 숫자로 쓸 때 (True = 1) */
internal fun bi(x: Boolean): Int = if (x) 1 else 0
