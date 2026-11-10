package com.kumhoht.chagokchagok.engine.synth

import android.content.Context
import android.util.Log
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * M1-1 확인용: 두 사운드폰트(SF3)의 악기를 구간별로 울려 WAV 로 렌더한다.
 * 구간마다 최대 음량·평균 음량을 재서 소리가 실제로 났는지 확인한다. (logcat 태그: SynthSmoke)
 *
 *   0~2초 피아노(GU)      2~4초 스트링(FR3)      4~6초 브라스+코러스(FR3)      6~8초 잔향
 *   베이스(GU)와 드럼(GU, bank 128)은 0~6초 내내
 */
object SynthSmokeTest {
    private const val TAG = "SynthSmoke"

    data class Section(val label: String, val peakDb: Double, val rmsDb: Double)
    data class Result(val file: File, val loadMillis: Long, val renderMillis: Long, val seconds: Double, val sections: List<Section>) {
        /** 소리가 난 구간만 통과로 본다 (RMS -50dB 초과) */
        val allAudible get() = sections.all { it.rmsDb > -50 }
        fun summary() = buildString {
            append("사운드폰트 로드 ${loadMillis}ms, 렌더 ${renderMillis}ms (곡 ${"%.1f".format(seconds)}초)\n")
            sections.forEach { append("${it.label}: 최대 ${"%.1f".format(it.peakDb)}dB, 평균 ${"%.1f".format(it.rmsDb)}dB\n") }
            append(if (allAudible) "결과: 모든 구간에서 소리 확인" else "결과: 소리가 안 난 구간 있음")
        }
    }

    fun run(context: Context): Result {
        val rate = 44_100
        val events = mutableListOf<SynthEvent>()
        fun f(sec: Double) = (sec * rate).toInt()
        fun note(ch: Int, key: Int, vel: Int, start: Double, len: Double) {
            events += SynthEvent.NoteOn(f(start), ch, key, vel)
            events += SynthEvent.NoteOff(f(start + len), ch, key)
        }

        // 채널: 0 피아노, 1 베이스, 2 스트링, 3 브라스, 4 코러스, 9 드럼
        events += SynthEvent.Program(0, 0, SoundFont.GENERAL_USER, 0, 0)     // Acoustic Grand Piano
        events += SynthEvent.Program(0, 1, SoundFont.GENERAL_USER, 0, 33)    // Electric Bass (finger)
        events += SynthEvent.Program(0, 2, SoundFont.FLUID_ENSEMBLE, 0, 48)  // Strings
        events += SynthEvent.Program(0, 3, SoundFont.FLUID_ENSEMBLE, 0, 61)  // Brass Section
        events += SynthEvent.Program(0, 4, SoundFont.FLUID_ENSEMBLE, 0, 52)  // Ahh Choir
        events += SynthEvent.Program(0, 9, SoundFont.GENERAL_USER, 128, 0)   // Standard Drum Kit

        val beat = 60.0 / 128 // 팝 128 BPM
        // 피아노 아르페지오 (0~2초)
        listOf(60, 64, 67, 72, 67, 64, 60, 64).forEachIndexed { i, k -> note(0, k, 100, i * beat / 2, beat / 2) }
        // 스트링 화음 (2~4초)
        listOf(48, 55, 60, 64).forEach { note(2, it, 90, 2.0, 2.0) }
        // 브라스 + 코러스 (4~6초)
        listOf(53, 57, 60, 65).forEach { note(3, it, 100, 4.0, 2.0) }
        listOf(65, 69, 72).forEach { note(4, it, 90, 4.0, 2.0) }
        // 베이스·드럼 (0~6초)
        var t = 0.0
        var i = 0
        while (t < 6.0 - 1e-6) {
            if (i % 2 == 0) note(1, if (t < 4.0) 36 else 41, 100, t, beat * 0.9)
            note(9, if (i % 2 == 0) 36 else 38, 110, t, 0.1)   // 킥 / 스네어
            note(9, 42, 70, t + beat / 2, 0.05)                 // 하이햇
            t += beat; i++
        }

        val totalSec = 8.0
        return OfflineRenderer(context, rate).use { r ->
            val t0 = System.currentTimeMillis()
            val pcm = r.render(events, f(totalSec))
            val renderMs = System.currentTimeMillis() - t0
            val out = File(context.filesDir, "smoke/synth_smoke.wav")
            writeWav(out, pcm, rate)

            val sections = listOf(
                Triple("0~2초 피아노+베이스+드럼", 0.0, 2.0),
                Triple("2~4초 스트링+베이스+드럼", 2.0, 4.0),
                Triple("4~6초 브라스+코러스+베이스+드럼", 4.0, 6.0),
                Triple("6~8초 잔향(꼬리)", 6.0, 8.0),
            ).map { (label, a, b) -> measure(label, pcm, f(a), f(b)) }

            // 악기별 단독 확인: 각 사운드폰트의 프리셋이 실제로 소리를 내는지 (다른 악기에 묻히지 않게 따로 렌더)
            val solos = listOf(
                Solo("단독 피아노 (GU 0:0)", SoundFont.GENERAL_USER, 0, 0, 0, 60),
                Solo("단독 베이스 (GU 0:33)", SoundFont.GENERAL_USER, 0, 33, 0, 36),
                Solo("단독 드럼 (GU 128:0)", SoundFont.GENERAL_USER, 128, 0, 9, 38),
                Solo("단독 스트링 (FR3 0:48)", SoundFont.FLUID_ENSEMBLE, 0, 48, 0, 60),
                Solo("단독 브라스 (FR3 0:61)", SoundFont.FLUID_ENSEMBLE, 0, 61, 0, 60),
                Solo("단독 코러스 (FR3 0:52)", SoundFont.FLUID_ENSEMBLE, 0, 52, 0, 60),
            ).map { s ->
                val solo = r.render(
                    listOf(
                        SynthEvent.Program(0, s.channel, s.font, s.bank, s.program),
                        SynthEvent.NoteOn(f(0.05), s.channel, s.key, 100),
                        SynthEvent.NoteOff(f(1.0), s.channel, s.key),
                    ),
                    f(1.5),
                )
                measure(s.label, solo, 0, f(1.5))
            }

            Result(out, r.loadMillis, renderMs, totalSec, sections + solos).also { Log.i(TAG, it.summary()) }
        }
    }

    private class Solo(val label: String, val font: SoundFont, val bank: Int, val program: Int, val channel: Int, val key: Int)

    private fun measure(label: String, pcm: FloatArray, from: Int, to: Int): Section {
        var peak = 0.0; var sum = 0.0
        for (i in from * 2 until to * 2) {
            val v = pcm[i].toDouble()
            peak = maxOf(peak, kotlin.math.abs(v)); sum += v * v
        }
        val rms = sqrt(sum / ((to - from) * 2))
        fun db(x: Double) = if (x <= 0) -120.0 else 20 * log10(x)
        return Section(label, db(peak), db(rms))
    }
}
