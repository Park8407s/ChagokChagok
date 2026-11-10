package com.kumhoht.chagokchagok.engine.composer

import com.kumhoht.chagokchagok.engine.session.Bar
import com.kumhoht.chagokchagok.engine.session.BarEvent
import kotlin.math.max
import kotlin.math.min

/**
 * 동요: drive_music_v2.py build() 의 genre == 'kids' 경로만 포팅.
 * (v3 에는 동요가 없다. v2 의 다른 장르 경로는 v3 로 대체되어 옮기지 않았다.)
 * 반주 = 오르골(musicbox), 베이스 = 쿵짝(oompah), 드럼 = 트라이앵글·우드블록(kids).
 */
internal object KidsComposerV2 {

    fun build(drive: List<Bar>): Score {
        val g = V2_KIDS
        val BEAT = 60.0 / g.bpm
        val BAR = BEAT * 4
        val ins = g.inst.mapValues { (name, prog) -> ScoreTrack(name, prog) }
        val drums = ScoreTrack("drums", 0, isDrum = true)
        val human = PyRandom(3)

        fun note(inst: ScoreTrack, p: Int, s: Double, d: Double, v: Int) {
            val j = human.uniform(-0.01, 0.01)
            inst.notes += ScoreNote(pitch = p, velocity = max(1, min(127, v)), start = max(0.0, s + j), end = s + max(d, .05))
        }
        fun note(k: String, p: Int, s: Double, d: Double, v: Int) = note(ins.getValue(k), p, s, d, v)
        fun dr(p: Int, s: Double, v: Int) = note(drums, p, s, .1, v)

        val hard = drive.count { it.ev == BarEvent.HARD_ACCEL || it.ev == BarEvent.HARD_BRAKE }
        val overs = drive.count { it.over }
        val good = hard <= 1 && overs == 0
        val rnd = PyRandom(11)
        val cache = HashMap<Triple<Int, String, Char?>, List<Triple<Double, Double, Int>>>()
        var melPrev = (g.melRange.first + g.melRange.second) / 2
        var ci = 0
        val (lo, hi) = g.melRange
        val pool = (lo..hi).filter { Math.floorMod(it, 12) in g.scale }

        for ((i, b) in drive.withIndex()) {
            val t = i * BAR
            val sp = b.speed
            val ev = b.ev

            // ---------- 인트로 ----------
            if (ev == BarEvent.INTRO) {
                val first = i == 0
                val tune = if (first) listOf(72, 76, 79, 84, 79, 76, 72, 67) else listOf(67, 71, 74, 79, 77, 74, 71, 67)
                for ((k, n) in tune.withIndex()) note("comp", n, t + k * BEAT / 2, BEAT, 75)
                if (!first) for (k in 0 until 4) dr(if (k == 0) 81 else 80, t + k * BEAT, 60)
                continue
            }

            // ---------- 아웃트로 ----------
            if (ev == BarEvent.OUTRO) {
                if (i == drive.size - 2) {
                    val end = if (good) g.goodEnd else g.badEnd
                    for (n in end) note("pad", n, t, BAR * 2, 62)
                    val se = end.sorted()
                    if (good) {
                        for ((k, n) in (se.drop(1) + (se.last() + 5)).withIndex()) note("comp", n, t + k * BEAT / 2, BAR * 2 - k * BEAT / 2, 60)
                        dr(81, t + BEAT * 2, 70)
                    } else {
                        for ((k, n) in se.drop(1).withIndex()) note("comp", n, t + k * BEAT / 2, BEAT * 1.5, 60)
                        note("comp", se[1] + 1, t + BAR, BAR, 45)
                    }
                }
                continue
            }

            val stopped = sp == 0
            var ch: List<Int>
            val root: Int
            if (stopped) {
                val c = g.chords.getValue(g.stop[(i / 8) % 2]); ch = c.first; root = c.second
            } else {
                val c = g.chords.getValue(g.prog[ci % g.prog.size]); ci += 1
                ch = c.first; root = c.second
                if (ev == BarEvent.HARD_ACCEL || ev == BarEvent.HARD_BRAKE || b.over) ch = tense(ch, root)
            }
            val cut = if (ev != BarEvent.HARD_BRAKE) BAR else BEAT * 2.5
            val energy = if (stopped) 0 else if (sp < 30) 1 else if (sp < 60) 2 else if (sp < 90) 3 else 4
            val pcs = ch.map { Math.floorMod(it, 12) }

            // ---------- 패드 ----------
            for (n in ch) note("pad", n, t, cut, 40 - 5 * bi(stopped))

            // ---------- 반주 (오르골) ----------
            if (stopped) {
                for ((k, n) in (ch + (ch[1] + 12)).withIndex()) note("comp", n + 12, t + k * BEAT, BEAT * 2, 55)
            } else {
                val arp = listOf(ch[0], ch[1], ch[2], ch[1])
                val step = if (energy <= 2) BEAT else BEAT / 2
                var k = 0
                while (k * step < cut - 1e-6) { note("comp", arp[k % 4] + 12, t + k * step, step * 1.2, 55 + energy * 3); k++ }
            }

            // ---------- 베이스 (쿵짝) ----------
            if (energy >= 1) {
                val pat = if (energy <= 2) listOf(Triple(0.0, 1.0, 0), Triple(2.0, 1.0, 7))
                else listOf(Triple(0.0, 1.0, 0), Triple(1.0, 1.0, 7), Triple(2.0, 1.0, 0), Triple(3.0, 1.0, 7))
                val r = if (root < 48) root else root - 12
                for ((s, d, off) in pat) if (s * BEAT < cut) note("bass", r + off, t + s * BEAT, d * BEAT * .9, 70)
            }

            // ---------- 드럼 (트라이앵글·탬버린) ----------
            fun until(s: Double) = s < cut - 1e-6
            if (energy >= 1) {
                dr(if (i % 2 == 0) 81 else 80, t, 55)
                for (bt in intArrayOf(1, 3)) if (until(bt * BEAT)) dr(39, t + bt * BEAT, 45 + 8 * energy)
                if (energy >= 3) for (k in 0 until 8) if (until(k * BEAT / 2)) dr(54, t + k * BEAT / 2, 40 + 10 * bi(k % 2 == 0))
                if (energy >= 4) for (bt in intArrayOf(0, 2)) dr(36, t + bt * BEAT, 55)
                if (b.over) for (k in 0 until 16) if (until(k * BEAT / 4)) dr(54, t + k * BEAT / 4, 60)
            }

            // ---------- 멜로디 ----------
            if (energy >= 2) {
                val lvl = if (energy == 2) "calm" else if (energy == 3) "mid" else "high"
                val key = Triple(Math.floorMod(ci - 1, g.prog.size), lvl, b.sig)
                if (key !in cache) {
                    val rhythm = g.rhythm.getValue(lvl)[((ci - 1) / g.prog.size) % 2]
                    // v2 는 v3 와 달리 진행 위치가 아니라 (ci-1)%4 로 오르내림을 정한다
                    val contour = if (b.sig == 'R') 1 else if (b.sig == 'L') -1 else (if (Math.floorMod(ci - 1, 4) < 2) 1 else -1)
                    val seq = mutableListOf<Triple<Double, Double, Int>>()
                    var prev = melPrev
                    for ((s, d) in rhythm) {
                        val cands = (if (s == 0.0 || s == 2.0) pool.filter { Math.floorMod(it, 12) in pcs } else pool).ifEmpty { pool }
                        val target = prev + contour * rnd.choice(STEPS)
                        val p = cands.minBy { kotlin.math.abs(it - target) }
                        seq += Triple(s, d, p); prev = p
                    }
                    cache[key] = seq
                }
                for ((s, d, p) in cache.getValue(key)) {
                    if (s * BEAT >= cut) break
                    val st = t + s * BEAT
                    note("mel", p, st, d * BEAT * .95, 82)
                    if (energy >= 4) note("mel2", p, st, d * BEAT, 55)
                    melPrev = p
                }
            }

            // ---------- 깜빡이 ----------
            if (b.sig != null) {
                var seq = ch.map { it + 24 }.toSortedSet().toList()
                if (b.sig == 'L') seq = seq.reversed()
                for (k in 0 until 8) note("spark", seq[k % seq.size], t + k * BEAT / 2, BEAT / 2, 62)
            }
            // ---------- 급가속 ----------
            if (ev == BarEvent.HARD_ACCEL) {
                val run = (60 until 100).filter { Math.floorMod(it, 12) in g.scale }.take(16)
                for ((k, n) in run.withIndex()) note("lead", n, t + BEAT * 2 + k * BEAT / 8, BEAT / 8, 72 + k)
                for (k in 0 until 4) dr(36, t + BEAT * 3 + k * BEAT / 4, 100)
            }
            // ---------- 급제동 ----------
            if (ev == BarEvent.HARD_BRAKE) {
                val s = t + cut
                for ((k, tm) in listOf(50, 47, 45).withIndex()) dr(tm, t + cut - BEAT * .75 + k * BEAT / 4, 85)
                dr(49, s, 110); dr(36, s, 115)
                note("acc", 43, s, BEAT, 110)
            }
            // ---------- 과속 ----------
            if (b.over) for (k in 0 until 8) note("lead", 84 + (k % 2), t + k * BEAT / 2, BEAT / 2.2, 55)
            // ---------- 부드러운 정차 ----------
            if (ev == BarEvent.SMOOTH_STOP) {
                val home = g.chords.getValue(g.prog[0]).first
                for (n in home) note("comp", n + 12, t + BEAT * 3, BEAT * 2, 55)
            }
        }
        return Score(g.bpm, ins.values.toList() + drums, drive.size)
    }

    private val STEPS = listOf(1, 2, 2, 3)
}
