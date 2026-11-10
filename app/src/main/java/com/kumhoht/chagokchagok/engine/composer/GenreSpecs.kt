package com.kumhoht.chagokchagok.engine.composer

/** (시작 박, 길이 박) */
typealias Rhythm = List<Pair<Double, Double>>

/** 원본 GENRES / G 사전 한 항목 */
class GenreSpec(
    val bpm: Int,
    val scale: List<Int>,
    val penta: List<Int>? = null,
    val melRange: Pair<Int, Int>,
    /** 이름 → (화음 음들, 베이스 근음) */
    val chords: Map<String, Pair<List<Int>, Int>>,
    val prog: List<String>,
    val stop: List<String>,
    val goodEnd: List<Int>,
    val badEnd: List<Int>,
    /** 악기 이름 → GM 프로그램 (정의 순서 유지) */
    val inst: LinkedHashMap<String, Int>,
    val rhythm: Map<String, List<Rhythm>>,
)

private fun r(vararg pairs: Pair<Number, Number>): Rhythm = pairs.map { it.first.toDouble() to it.second.toDouble() }
private fun ch(vararg c: Pair<String, Pair<List<Int>, Int>>) = linkedMapOf(*c)
private fun inst(vararg c: Pair<String, Int>) = linkedMapOf(*c)

/** drive_music_v3.py GENRES (미국 팝·발라드·트로트·댄스) */
internal val V3_GENRES: Map<String, GenreSpec> = mapOf(
    "uspop" to GenreSpec(
        bpm = 128, scale = listOf(0, 2, 4, 5, 7, 9, 11), melRange = 67 to 86,
        chords = ch("C" to (listOf(60, 64, 67) to 36), "G" to (listOf(59, 62, 67) to 43), "Am" to (listOf(60, 64, 69) to 45), "F" to (listOf(60, 65, 69) to 41)),
        prog = listOf("C", "G", "Am", "F"), stop = listOf("Am", "F"),
        goodEnd = listOf(36, 48, 55, 60, 64, 67, 74), badEnd = listOf(43, 55, 60, 62, 67),
        inst = inst("pad" to 89, "comp" to 0, "gtr" to 28, "mel" to 4, "mel2" to 53, "bass" to 39, "spark" to 9, "lead" to 81, "acc" to 0),
        rhythm = mapOf(
            "calm" to listOf(r(0 to 1.5, 1.5 to 1, 3 to 1)),
            "mid" to listOf(r(0 to .5, .5 to .5, 1 to 1, 2.5 to .5, 3 to 1), r(.5 to .5, 1 to .5, 1.5 to 1, 3 to .5, 3.5 to .5)),
            "high" to listOf(r(0 to .5, .5 to .5, 1 to .5, 1.5 to 1, 3 to .5, 3.5 to .5), r(0 to .75, .75 to .75, 1.5 to .5, 2 to 1, 3.5 to .5)),
        ),
    ),
    "ballad" to GenreSpec(
        bpm = 72, scale = listOf(0, 2, 4, 5, 7, 9, 11), melRange = 67 to 86,
        chords = ch(
            "C" to (listOf(55, 60, 64) to 48), "G/B" to (listOf(55, 59, 62) to 47), "Am" to (listOf(57, 60, 64) to 45), "Am/G" to (listOf(57, 60, 64) to 43),
            "F" to (listOf(57, 60, 65) to 41), "C/E" to (listOf(55, 60, 64) to 40), "Dm7" to (listOf(57, 60, 62, 65) to 38), "G" to (listOf(55, 59, 62, 65) to 43),
            "Fmaj7" to (listOf(57, 60, 64, 65) to 41), "Em7" to (listOf(55, 59, 62, 64) to 40),
        ),
        prog = listOf("C", "G/B", "Am", "Am/G", "F", "C/E", "Dm7", "G"), stop = listOf("Fmaj7", "Em7"),
        goodEnd = listOf(36, 48, 55, 60, 64, 67, 74), badEnd = listOf(43, 55, 60, 62, 67),
        inst = inst("pad" to 48, "comp" to 0, "mel" to 0, "mel2" to 48, "cello" to 42, "bass" to 32, "spark" to 8, "lead" to 40, "acc" to 0),
        rhythm = mapOf(
            "calm" to listOf(r(0 to 3, 3 to 1), r(0 to 2, 2 to 1.5, 3.5 to .5)),
            "mid" to listOf(r(0 to 1.5, 1.5 to .5, 2 to 1, 3 to 1), r(0 to 1, 1 to 1, 2 to 2)),
            "high" to listOf(r(0 to 1, 1 to .5, 1.5 to .5, 2 to 1.5, 3.5 to .5), r(0 to .5, .5 to .5, 1 to 1, 2 to 1, 3 to 1)),
        ),
    ),
    "trot" to GenreSpec(
        bpm = 134, scale = listOf(9, 11, 0, 2, 4, 5, 7), penta = listOf(9, 0, 2, 4, 7), melRange = 64 to 84,
        chords = ch("Am" to (listOf(57, 60, 64) to 45), "Dm" to (listOf(57, 62, 65) to 38), "E7" to (listOf(56, 59, 62, 64) to 40), "F" to (listOf(57, 60, 65) to 41)),
        prog = listOf("Am", "Dm", "E7", "Am", "F", "Dm", "E7", "Am"), stop = listOf("Dm", "Am"),
        goodEnd = listOf(45, 57, 60, 64, 69, 72), badEnd = listOf(40, 56, 59, 62, 64),
        inst = inst("pad" to 16, "comp" to 16, "gtr" to 27, "mel" to 65, "mel2" to 56, "bass" to 33, "spark" to 11, "lead" to 56, "acc" to 61),
        rhythm = mapOf(
            "calm" to listOf(r(0 to 3, 3 to 1), r(0 to 1.5, 1.5 to 2.5)),
            "mid" to listOf(r(0 to 1.5, 1.5 to .5, 2 to 2), r(0 to .5, .5 to 1, 1.5 to .5, 2 to 2)),
            "high" to listOf(r(0 to 1, 1 to .5, 1.5 to 1, 2.5 to .5, 3 to 1), r(0 to 1.5, 1.5 to .5, 2 to 1, 3 to 1)),
        ),
    ),
    "dance" to GenreSpec(
        bpm = 140, scale = listOf(9, 11, 0, 2, 4, 5, 7), melRange = 69 to 88,
        chords = ch("Am" to (listOf(57, 60, 64) to 45), "F" to (listOf(57, 60, 65) to 41), "C" to (listOf(55, 60, 64) to 48), "G" to (listOf(55, 59, 62) to 43)),
        prog = listOf("Am", "F", "C", "G"), stop = listOf("F", "G"),
        goodEnd = listOf(45, 57, 64, 67, 71, 76), badEnd = listOf(43, 55, 59, 62, 68),
        inst = inst("pad" to 90, "comp" to 80, "mel" to 81, "mel2" to 80, "bass" to 38, "spark" to 98, "lead" to 81, "acc" to 55),
        rhythm = mapOf(
            "calm" to listOf(r(0 to 2, 2 to 2)),
            "mid" to listOf(r(0 to .75, .75 to .75, 1.5 to 1, 3 to 1)),
            "high" to listOf(r(0 to .5, .75 to .5, 1.5 to .5, 2 to .5, 2.75 to .5, 3.5 to .5), r(0 to .5, .5 to .5, 1 to .75, 1.75 to .75, 2.5 to .5, 3 to 1)),
        ),
    ),
)

/** drive_music_v2.py G['kids'] (동요) — v3 에는 동요가 없어 v2 정의를 쓴다 */
internal val V2_KIDS = GenreSpec(
    bpm = 112, scale = listOf(0, 2, 4, 5, 7, 9, 11), melRange = 72 to 88,
    chords = ch("C" to (listOf(60, 64, 67) to 48), "F" to (listOf(60, 65, 69) to 53), "G7" to (listOf(59, 62, 65, 67) to 43), "Am" to (listOf(57, 60, 64) to 45)),
    prog = listOf("C", "F", "G7", "C", "C", "Am", "G7", "C"), stop = listOf("F", "C"),
    goodEnd = listOf(48, 60, 64, 67, 72, 76), badEnd = listOf(43, 55, 60, 62, 65),
    inst = inst("pad" to 48, "comp" to 10, "mel" to 74, "mel2" to 13, "bass" to 45, "spark" to 9, "lead" to 13, "acc" to 47),
    rhythm = mapOf(
        "calm" to listOf(r(0 to 1, 1 to 1, 2 to 2), r(0 to 2, 2 to 2)),
        "mid" to listOf(r(0 to 1, 1 to 1, 2 to 1, 3 to 1), r(0 to 1, 1 to 1, 2 to 2)),
        "high" to listOf(r(0 to .5, .5 to .5, 1 to 1, 2 to .5, 2.5 to .5, 3 to 1), r(0 to 1, 1 to .5, 1.5 to .5, 2 to 2)),
    ),
)

/** 3도 → 4도(서스), b7 추가 = 불안정하지만 음악적인 긴장 화음 (v2·v3 같은 결과) */
internal fun tense(notes: List<Int>, root: Int): List<Int> {
    val r = Math.floorMod(root, 12)
    val out = notes.map { n ->
        val pc = Math.floorMod(n - r, 12)
        n + if (pc == 4) 1 else if (pc == 3) 2 else 0
    }
    val top = out.max()
    var b7 = top - Math.floorMod(top - r, 12) + 10
    if (b7 <= top) b7 += 12
    if (b7 - 12 > out.min()) b7 -= 12
    return (out + b7).toSortedSet().toList()
}
