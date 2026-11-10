package com.kumhoht.chagokchagok.tools.sf2

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.log10

/**
 * FFmpeg(libvorbis) 로 16bit 모노 PCM ↔ Ogg Vorbis 변환.
 * 개발 PC 에서 한 번 변환할 때만 쓴다 (앱에서는 FluidSynth 가 직접 압축을 푼다).
 */
class Vorbis(private val ffmpeg: String, private val quality: Double) {

    fun encode(pcm: ByteArray, sampleRate: Long): ByteArray = run(
        listOf(
            "-f", "s16le", "-ar", sampleRate.toString(), "-ac", "1", "-i", "pipe:0",
            // 용량을 줄이려고 메타데이터(인코더 이름 등)는 넣지 않는다
            "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
            "-c:a", "libvorbis", "-q:a", quality.toString(), "-f", "ogg", "pipe:1",
        ),
        pcm,
    )

    fun decode(ogg: ByteArray): ByteArray =
        run(listOf("-i", "pipe:0", "-f", "s16le", "-ac", "1", "pipe:1"), ogg)

    private fun run(args: List<String>, input: ByteArray): ByteArray {
        val p = ProcessBuilder(listOf(ffmpeg, "-v", "error", "-nostdin") + args).start()
        val err = ByteArrayOutputStream()
        val errThread = thread { p.errorStream.copyTo(err) }
        val inThread = thread { p.outputStream.use { it.write(input) } }
        val out = p.inputStream.readBytes()
        inThread.join(); errThread.join()
        val code = p.waitFor()
        check(code == 0 && out.isNotEmpty()) { "ffmpeg 실패(code $code): ${err.toString(Charsets.UTF_8).trim()}" }
        return out
    }
}

/** 원본 대비 복원 신호의 신호대잡음비(dB). 정렬이 어긋났는지 보는 참고값 (지각 음질 지표는 아님). */
fun snrDb(original: ByteArray, decoded: ByteArray): Double {
    val a = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    val b = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    val n = minOf(a.remaining(), b.remaining())
    var sig = 0.0; var noise = 0.0
    for (i in 0 until n) {
        val x = a.get(i).toDouble(); val d = x - b.get(i)
        sig += x * x; noise += d * d
    }
    if (sig == 0.0) return Double.POSITIVE_INFINITY
    if (noise == 0.0) return 200.0
    return 10 * log10(sig / noise)
}
