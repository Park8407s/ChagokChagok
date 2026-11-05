package com.kumhoht.todaysost.tools.sf2

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * SoundFont 2 (.sf2) 읽기·쓰기 최소 구현 (SoundFont 2.04 규격).
 *
 * 파일 구조
 *   RIFF 'sfbk'
 *     LIST 'INFO'  ifil, INAM, ICOP ... (문자열 정보)
 *     LIST 'sdta'  smpl (16bit PCM 전체를 이어 붙인 덩어리), sm24 (선택)
 *     LIST 'pdta'  phdr→pbag→pgen/pmod (프리셋) → inst→ibag→igen/imod (악기) → shdr (샘플 헤더)
 * pdta 의 각 표는 마지막에 '끝 표시' 레코드(EOP/EOI/EOS 등)를 하나 더 가진다.
 */

class Phdr(
    val name: ByteArray, val program: Int, val bank: Int, val bagNdx: Int,
    val library: Long, val genre: Long, val morphology: Long,
) {
    val nameText: String get() = cString(name)
}

class Bag(val genNdx: Int, val modNdx: Int)

/** 생성자(generator). amount 는 원본 2바이트를 부호 없는 값으로 그대로 둔다. */
class Gen(val oper: Int, val amount: Int)

class Inst(val name: ByteArray, val bagNdx: Int) {
    val nameText: String get() = cString(name)
}

class Shdr(
    val name: ByteArray, val start: Long, val end: Long, val startLoop: Long, val endLoop: Long,
    val sampleRate: Long, val originalPitch: Int, val pitchCorrection: Int,
    val sampleLink: Int, val sampleType: Int,
) {
    val isRom: Boolean get() = sampleType and 0x8000 != 0
    /** 2=right, 4=left, 8=linked: 짝 샘플이 있는 종류 */
    val isLinked: Boolean get() = !isRom && (sampleType and 0x0E) != 0
}

class Sf2(
    val info: List<Pair<String, ByteArray>>,
    /** 16bit 샘플 데이터 (리틀 엔디언 원본 바이트) */
    val smpl: ByteArray,
    val hasSm24: Boolean,
    val phdr: List<Phdr>, val pbag: List<Bag>, val pmod: List<ByteArray>, val pgen: List<Gen>,
    val inst: List<Inst>, val ibag: List<Bag>, val imod: List<ByteArray>, val igen: List<Gen>,
    val shdr: List<Shdr>,
) {
    /** 끝 표시 레코드를 뺀 실제 개수 */
    val presetCount get() = phdr.size - 1
    val instCount get() = inst.size - 1
    val sampleCount get() = shdr.size - 1
}

object Gens {
    const val INSTRUMENT = 41
    const val SAMPLE_ID = 53
}

const val MOD_SIZE = 10

fun cString(b: ByteArray): String = b.toString(Charsets.ISO_8859_1).substringBefore('\u0000').trim()

private fun fixedName(text: String): ByteArray =
    ByteArray(20).also { text.toByteArray(Charsets.ISO_8859_1).copyInto(it, 0, 0, minOf(text.length, 19)) }

// ───────────────────────────── 읽기 ─────────────────────────────

fun readSf2(file: File): Sf2 {
    val raw = file.readBytes()
    val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    fun fourcc(p: Int) = String(raw, p, 4, Charsets.ISO_8859_1)
    fun u32(p: Int) = bb.getInt(p).toLong() and 0xFFFFFFFFL

    require(fourcc(0) == "RIFF" && fourcc(8) == "sfbk") { "SF2 파일이 아닙니다: ${file.name}" }
    val end = minOf(raw.size.toLong(), 8 + u32(4)).toInt()

    // LIST 종류 → (청크 ID, 데이터 시작, 길이) 목록
    val lists = mutableMapOf<String, MutableList<Triple<String, Int, Int>>>()
    var pos = 12
    while (pos + 8 <= end) {
        val id = fourcc(pos)
        val size = u32(pos + 4).toInt()
        if (id == "LIST") {
            val type = fourcc(pos + 8)
            val subs = lists.getOrPut(type) { mutableListOf() }
            var p = pos + 12
            val listEnd = pos + 8 + size
            while (p + 8 <= listEnd) {
                val sid = fourcc(p)
                val ssize = u32(p + 4).toInt()
                subs += Triple(sid, p + 8, ssize)
                p += 8 + ssize + (ssize and 1)
            }
        }
        pos += 8 + size + (size and 1)
    }

    fun sub(list: String, id: String): Triple<String, Int, Int> =
        lists[list]?.firstOrNull { it.first == id } ?: error("$list/$id 청크가 없습니다")

    fun <T> records(id: String, recSize: Int, parse: (Int) -> T): List<T> {
        val (_, off, size) = sub("pdta", id)
        require(size % recSize == 0) { "$id 길이($size)가 레코드 크기($recSize)의 배수가 아닙니다" }
        return (0 until size / recSize).map { parse(off + it * recSize) }
    }

    fun u16(p: Int) = bb.getShort(p).toInt() and 0xFFFF
    fun name(p: Int) = raw.copyOfRange(p, p + 20)

    val info = lists["INFO"].orEmpty().map { (id, off, size) -> id to raw.copyOfRange(off, off + size) }
    val (_, smplOff, smplSize) = sub("sdta", "smpl")

    return Sf2(
        info = info,
        smpl = raw.copyOfRange(smplOff, smplOff + smplSize),
        hasSm24 = lists["sdta"].orEmpty().any { it.first == "sm24" },
        phdr = records("phdr", 38) { p -> Phdr(name(p), u16(p + 20), u16(p + 22), u16(p + 24), u32(p + 26), u32(p + 30), u32(p + 34)) },
        pbag = records("pbag", 4) { p -> Bag(u16(p), u16(p + 2)) },
        pmod = records("pmod", MOD_SIZE) { p -> raw.copyOfRange(p, p + MOD_SIZE) },
        pgen = records("pgen", 4) { p -> Gen(u16(p), u16(p + 2)) },
        inst = records("inst", 22) { p -> Inst(name(p), u16(p + 20)) },
        ibag = records("ibag", 4) { p -> Bag(u16(p), u16(p + 2)) },
        imod = records("imod", MOD_SIZE) { p -> raw.copyOfRange(p, p + MOD_SIZE) },
        igen = records("igen", 4) { p -> Gen(u16(p), u16(p + 2)) },
        shdr = records("shdr", 46) { p ->
            Shdr(
                name(p), u32(p + 20), u32(p + 24), u32(p + 28), u32(p + 32), u32(p + 36),
                raw[p + 40].toInt() and 0xFF, raw[p + 41].toInt(), u16(p + 42), u16(p + 44),
            )
        },
    )
}

// ───────────────────────────── 검사 ─────────────────────────────

/** 표 사이의 색인이 서로 맞는지 확인한다. 문제 목록을 돌려준다 (비어 있으면 정상). */
fun validate(sf: Sf2): List<String> {
    val errs = mutableListOf<String>()
    val sampleFrames = sf.smpl.size / 2
    for (i in 0 until sf.presetCount) {
        if (sf.phdr[i].bagNdx > sf.phdr[i + 1].bagNdx) errs += "프리셋 $i: bag 색인이 거꾸로입니다"
    }
    if (sf.phdr.last().bagNdx != sf.pbag.size - 1) errs += "EOP 의 bag 색인이 pbag 끝과 다릅니다"
    if (sf.inst.last().bagNdx != sf.ibag.size - 1) errs += "EOI 의 bag 색인이 ibag 끝과 다릅니다"
    if (sf.pbag.last().genNdx != sf.pgen.size - 1) errs += "pbag 끝 표시의 gen 색인이 pgen 끝과 다릅니다"
    if (sf.ibag.last().genNdx != sf.igen.size - 1) errs += "ibag 끝 표시의 gen 색인이 igen 끝과 다릅니다"
    sf.pgen.forEachIndexed { i, g ->
        if (g.oper == Gens.INSTRUMENT && g.amount >= sf.instCount) errs += "pgen $i: 없는 악기 ${g.amount}"
    }
    sf.igen.forEachIndexed { i, g ->
        if (g.oper == Gens.SAMPLE_ID && g.amount >= sf.sampleCount) errs += "igen $i: 없는 샘플 ${g.amount}"
    }
    for (i in 0 until sf.sampleCount) {
        val s = sf.shdr[i]
        if (s.isRom) continue
        if (s.end > sampleFrames || s.start > s.end) errs += "샘플 $i(${cString(s.name)}): 범위가 smpl 밖입니다"
        if (s.isLinked && s.sampleLink >= sf.sampleCount) errs += "샘플 $i: 없는 짝 샘플 ${s.sampleLink}"
    }
    return errs
}

// ───────────────────────────── 부분 추출 ─────────────────────────────

/**
 * [keep] 이 true 인 프리셋만 남긴 새 SF2 를 만든다.
 * 그 프리셋이 쓰는 악기와 샘플(스테레오 짝 포함)만 따라 남기고, 색인은 모두 새로 매긴다.
 * 샘플 데이터는 남긴 샘플만 이어 붙이며, 규격대로 샘플 뒤에 0 을 46개 넣는다.
 */
fun subset(src: Sf2, infoOverrides: Map<String, String>, keep: (bank: Int, program: Int) -> Boolean): Sf2 {
    require(src.shdr.dropLast(1).none { it.isRom }) { "ROM 샘플이 있는 SF2 는 지원하지 않습니다" }

    val keptPresets = (0 until src.presetCount).filter { keep(src.phdr[it].bank, src.phdr[it].program) }
    require(keptPresets.isNotEmpty()) { "조건에 맞는 프리셋이 없습니다" }

    fun presetBags(i: Int) = src.phdr[i].bagNdx until src.phdr[i + 1].bagNdx
    fun instBags(i: Int) = src.inst[i].bagNdx until src.inst[i + 1].bagNdx
    fun gens(bags: List<Bag>, b: Int) = bags[b].genNdx until bags[b + 1].genNdx
    fun mods(bags: List<Bag>, b: Int) = bags[b].modNdx until bags[b + 1].modNdx

    // 1) 남길 악기
    val instSet = sortedSetOf<Int>()
    for (p in keptPresets) for (b in presetBags(p)) for (g in gens(src.pbag, b)) {
        if (src.pgen[g].oper == Gens.INSTRUMENT) instSet += src.pgen[g].amount
    }
    val instMap = instSet.withIndex().associate { (new, old) -> old to new }

    // 2) 남길 샘플 (+ 스테레오 짝)
    val sampleSet = sortedSetOf<Int>()
    for (i in instSet) for (b in instBags(i)) for (g in gens(src.ibag, b)) {
        if (src.igen[g].oper == Gens.SAMPLE_ID) sampleSet += src.igen[g].amount
    }
    do {
        val links = sampleSet.map { src.shdr[it] }.filter { it.isLinked }.map { it.sampleLink }
            .filter { it < src.sampleCount && it !in sampleSet }
        sampleSet += links
    } while (links.isNotEmpty())
    val sampleMap = sampleSet.withIndex().associate { (new, old) -> old to new }

    // 3) 샘플 데이터 다시 이어 붙이기
    val smpl = ByteArrayOutputStream()
    val pad = ByteArray(46 * 2)
    val shdr = sampleSet.map { old ->
        val s = src.shdr[old]
        val newStart = (smpl.size() / 2).toLong()
        smpl.write(src.smpl, (s.start * 2).toInt(), ((s.end - s.start) * 2).toInt())
        smpl.write(pad)
        val shift = newStart - s.start
        Shdr(
            s.name, newStart, s.end + shift,
            maxOf(newStart, s.startLoop + shift), maxOf(newStart, s.endLoop + shift),
            s.sampleRate, s.originalPitch, s.pitchCorrection,
            if (s.isLinked) sampleMap.getValue(s.sampleLink) else 0, s.sampleType,
        )
    } + Shdr(fixedName("EOS"), 0, 0, 0, 0, 0, 0, 0, 0, 0)

    // 4) 악기 표
    val inst = mutableListOf<Inst>(); val ibag = mutableListOf<Bag>()
    val igen = mutableListOf<Gen>(); val imod = mutableListOf<ByteArray>()
    for (old in instSet) {
        inst += Inst(src.inst[old].name, ibag.size)
        for (b in instBags(old)) {
            ibag += Bag(igen.size, imod.size)
            for (g in gens(src.ibag, b)) {
                val gen = src.igen[g]
                igen += if (gen.oper == Gens.SAMPLE_ID) Gen(gen.oper, sampleMap.getValue(gen.amount)) else gen
            }
            for (m in mods(src.ibag, b)) imod += src.imod[m]
        }
    }
    inst += Inst(fixedName("EOI"), ibag.size)
    ibag += Bag(igen.size, imod.size)
    igen += Gen(0, 0)
    imod += ByteArray(MOD_SIZE)

    // 5) 프리셋 표
    val phdr = mutableListOf<Phdr>(); val pbag = mutableListOf<Bag>()
    val pgen = mutableListOf<Gen>(); val pmod = mutableListOf<ByteArray>()
    for (old in keptPresets) {
        val p = src.phdr[old]
        phdr += Phdr(p.name, p.program, p.bank, pbag.size, p.library, p.genre, p.morphology)
        for (b in presetBags(old)) {
            pbag += Bag(pgen.size, pmod.size)
            for (g in gens(src.pbag, b)) {
                val gen = src.pgen[g]
                pgen += if (gen.oper == Gens.INSTRUMENT) Gen(gen.oper, instMap.getValue(gen.amount)) else gen
            }
            for (m in mods(src.pbag, b)) pmod += src.pmod[m]
        }
    }
    phdr += Phdr(fixedName("EOP"), 0, 0, pbag.size, 0, 0, 0)
    pbag += Bag(pgen.size, pmod.size)
    pgen += Gen(0, 0)
    pmod += ByteArray(MOD_SIZE)

    // 6) INFO: 원본 저작권 문구는 그대로 두고, 지정한 항목만 바꾸거나 덧붙인다
    val info = src.info.map { (id, data) ->
        infoOverrides[id]?.let { id to infoString(it) } ?: (id to data)
    } + infoOverrides.filterKeys { k -> src.info.none { it.first == k } }.map { (k, v) -> k to infoString(v) }

    return Sf2(info, smpl.toByteArray(), false, phdr, pbag, pmod, pgen, inst, ibag, imod, igen, shdr)
}

/** INFO 문자열: 0 으로 끝나고 길이는 짝수 */
private fun infoString(text: String): ByteArray {
    val b = text.toByteArray(Charsets.ISO_8859_1)
    val len = b.size + 1 + ((b.size + 1) and 1)
    return b.copyOf(len)
}

// ───────────────────────────── 쓰기 ─────────────────────────────

private class LeOut {
    val out = ByteArrayOutputStream()
    fun u8(v: Int) = out.write(v and 0xFF)
    fun u16(v: Int) { u8(v); u8(v shr 8) }
    fun u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
    fun bytes(b: ByteArray) = out.write(b)
}

private fun chunk(id: String, data: ByteArray): ByteArray = LeOut().apply {
    bytes(id.toByteArray(Charsets.ISO_8859_1)); u32(data.size.toLong()); bytes(data)
    if (data.size and 1 == 1) u8(0)
}.out.toByteArray()

private fun listChunk(type: String, children: List<ByteArray>): ByteArray {
    val body = ByteArrayOutputStream()
    body.write(type.toByteArray(Charsets.ISO_8859_1))
    children.forEach { body.write(it) }
    return chunk("LIST", body.toByteArray())
}

private fun <T> table(items: List<T>, write: LeOut.(T) -> Unit): ByteArray =
    LeOut().apply { items.forEach { write(it) } }.out.toByteArray()

fun writeSf2(sf: Sf2, file: File) {
    require(!sf.hasSm24) { "sm24 쓰기는 지원하지 않습니다" }
    val bag: LeOut.(Bag) -> Unit = { u16(it.genNdx); u16(it.modNdx) }
    val gen: LeOut.(Gen) -> Unit = { u16(it.oper); u16(it.amount) }
    val mod: LeOut.(ByteArray) -> Unit = { bytes(it) }

    val info = listChunk("INFO", sf.info.map { (id, data) -> chunk(id, data) })
    val sdta = listChunk("sdta", listOf(chunk("smpl", sf.smpl)))
    val pdta = listChunk(
        "pdta", listOf(
            chunk("phdr", table(sf.phdr) {
                bytes(it.name); u16(it.program); u16(it.bank); u16(it.bagNdx)
                u32(it.library); u32(it.genre); u32(it.morphology)
            }),
            chunk("pbag", table(sf.pbag, bag)),
            chunk("pmod", table(sf.pmod, mod)),
            chunk("pgen", table(sf.pgen, gen)),
            chunk("inst", table(sf.inst) { bytes(it.name); u16(it.bagNdx) }),
            chunk("ibag", table(sf.ibag, bag)),
            chunk("imod", table(sf.imod, mod)),
            chunk("igen", table(sf.igen, gen)),
            chunk("shdr", table(sf.shdr) {
                bytes(it.name); u32(it.start); u32(it.end); u32(it.startLoop); u32(it.endLoop)
                u32(it.sampleRate); u8(it.originalPitch); u8(it.pitchCorrection)
                u16(it.sampleLink); u16(it.sampleType)
            }),
        )
    )
    file.parentFile?.mkdirs()
    file.outputStream().buffered().use { os ->
        os.write("RIFF".toByteArray())
        val size = 4L + info.size + sdta.size + pdta.size
        os.write(LeOut().apply { u32(size) }.out.toByteArray())
        os.write("sfbk".toByteArray())
        os.write(info); os.write(sdta); os.write(pdta)
    }
}
