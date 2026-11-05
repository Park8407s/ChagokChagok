package com.kumhoht.todaysost.tools.sf2

import java.io.File
import kotlin.system.exitProcess

/**
 * 사운드폰트에서 필요한 프리셋만 뽑아 작은 SF2 로 만드는 도구.
 * (FluidR3_GM 원본은 약 141MB 라 GitHub 파일 한도 100MB 를 넘는다.)
 *
 * 사용법 (프로젝트 루트에서):
 *   ./gradlew :tools:sf2subset:run --args="list <원본.sf2>"
 *   ./gradlew :tools:sf2subset:run --args="extract <원본.sf2> <결과.sf2> <프리셋 목록>"
 *
 * 프리셋 목록 형식: "뱅크:프로그램" 을 쉼표로 구분. 프로그램은 범위(40-45)나 * 를 쓸 수 있다.
 *   예) "0:40-45,0:48-55,0:56-63"   (GM 번호는 0부터 센다)
 */
fun main(args: Array<String>) {
    when (args.getOrNull(0)) {
        "list" -> list(File(args[1]))
        "extract" -> extract(File(args[1]), File(args[2]), args[3])
        else -> {
            System.err.println("사용법: list <in.sf2> | extract <in.sf2> <out.sf2> <뱅크:프로그램,...>")
            exitProcess(1)
        }
    }
}

private fun list(file: File) {
    val sf = readSf2(file)
    println("${file.name}: 프리셋 ${sf.presetCount}, 악기 ${sf.instCount}, 샘플 ${sf.sampleCount}, 샘플 데이터 ${mb(sf.smpl.size.toLong())}")
    sf.phdr.dropLast(1).sortedWith(compareBy({ it.bank }, { it.program })).forEach {
        println("%3d:%-3d  %s".format(it.bank, it.program, it.nameText))
    }
    report(validate(sf))
}

private fun extract(input: File, output: File, spec: String) {
    val rules = parseSpec(spec)
    val src = readSf2(input)
    report(validate(src), what = "원본")

    val origName = src.info.firstOrNull { it.first == "INAM" }?.let { cString(it.second) } ?: input.nameWithoutExtension
    val sub = subset(
        src,
        infoOverrides = mapOf(
            "INAM" to "$origName (TodaysOST subset)",
            "ICMT" to "Subset of ${input.name} containing presets $spec. " +
                "Extracted by TodaysOST sf2subset; samples and parameters are unmodified.",
        ),
    ) { bank, program -> rules.any { it(bank, program) } }
    writeSf2(sub, output)

    // 다시 읽어서 확인
    val check = readSf2(output)
    println("원본: ${mb(input.length())}  →  결과: ${mb(output.length())}  (${output.path})")
    println("프리셋 ${check.presetCount}, 악기 ${check.instCount}, 샘플 ${check.sampleCount}")
    check.phdr.dropLast(1).forEach { println("%3d:%-3d  %s".format(it.bank, it.program, it.nameText)) }
    report(validate(check), what = "결과")
    report(compareSamples(src, check), what = "샘플 데이터 대조")
}

/** 결과의 샘플마다 원본에 이름·길이·소리 데이터가 똑같은 샘플이 있는지 확인한다. */
private fun compareSamples(src: Sf2, out: Sf2): List<String> {
    fun pcm(sf: Sf2, s: Shdr) = sf.smpl.copyOfRange((s.start * 2).toInt(), (s.end * 2).toInt())
    val byKey = src.shdr.dropLast(1).groupBy { cString(it.name) to (it.end - it.start) }
    return out.shdr.dropLast(1).mapNotNull { s ->
        val data = pcm(out, s)
        val same = byKey[cString(s.name) to (s.end - s.start)].orEmpty().any { o ->
            pcm(src, o).contentEquals(data) &&
                o.startLoop - o.start == s.startLoop - s.start && o.endLoop - o.start == s.endLoop - s.start
        }
        if (same) null else "샘플 ${cString(s.name)}: 원본과 다릅니다"
    }
}

private fun parseSpec(spec: String): List<(Int, Int) -> Boolean> = spec.split(',').map { part ->
    val (bankStr, progStr) = part.trim().split(':').also { require(it.size == 2) { "잘못된 형식: $part" } }
    val bank = bankStr.toInt()
    val progs: IntRange = when {
        progStr == "*" -> 0..127
        '-' in progStr -> progStr.split('-').let { it[0].toInt()..it[1].toInt() }
        else -> progStr.toInt().let { it..it }
    }
    val rule: (Int, Int) -> Boolean = { b, p -> b == bank && p in progs }
    rule
}

private fun report(errors: List<String>, what: String = "구조") {
    if (errors.isEmpty()) {
        println("$what 검사: 이상 없음")
    } else {
        System.err.println("$what 검사: 문제 ${errors.size}건")
        errors.take(20).forEach { System.err.println("  - $it") }
        exitProcess(2)
    }
}

private fun mb(bytes: Long) = "%.1fMB".format(bytes / 1024.0 / 1024.0)
