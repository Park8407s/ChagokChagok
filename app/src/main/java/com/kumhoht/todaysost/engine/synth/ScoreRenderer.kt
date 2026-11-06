package com.kumhoht.todaysost.engine.synth

import com.kumhoht.todaysost.engine.composer.Score
import kotlin.math.roundToInt

/**
 * 악보 → FluidSynth 이벤트.
 * - 원본은 pretty_midi 로 MIDI 파일을 써서 fluidsynth 로 렌더했다. 여기서는 같은 내용을 이벤트로 바로 넘긴다.
 * - 채널: 악기마다 1개 (9번은 드럼 전용, pretty_midi 와 같은 규칙)
 * - 사운드폰트: 스트링·코러스·브라스(GM 40~45, 48~54, 56~63)는 FluidR3 추출본, 나머지와 드럼은 GeneralUser GS
 */
object ScoreRenderer {
    /** 렌더 끝에 붙이는 여유 (잔향·페이드 아웃). 원본 ffmpeg 페이드가 곡 끝 2초 전부터 4초 */
    const val TAIL_SEC = 2.0

    private val ENSEMBLE = (40..45) + (48..54) + (56..63)

    fun fontFor(program: Int) = if (program in ENSEMBLE) SoundFont.FLUID_ENSEMBLE else SoundFont.GENERAL_USER

    fun events(score: Score, sampleRate: Int): List<SynthEvent> {
        fun f(sec: Double) = (sec * sampleRate).roundToInt()
        // (frame, 같은 시점 처리 순서, 이벤트): 악기 선택 → 음 끄기 → 벤드 → 음 켜기
        val out = mutableListOf<Triple<Int, Int, SynthEvent>>()
        var next = 0
        for (track in score.tracks.filter { it.notes.isNotEmpty() || it.bends.isNotEmpty() }) {
            val ch = if (track.isDrum) 9 else next.also { next = if (next + 1 == 9) 10 else next + 1 }
            require(ch < 16) { "채널이 부족합니다 (악기 ${score.tracks.size}개)" }
            val prog = if (track.isDrum) SynthEvent.Program(0, ch, SoundFont.GENERAL_USER, 128, 0)
            else SynthEvent.Program(0, ch, fontFor(track.program), 0, track.program)
            out += Triple(0, 0, prog)
            for (n in track.notes) {
                out += Triple(f(n.start), 3, SynthEvent.NoteOn(f(n.start), ch, n.pitch, n.velocity))
                out += Triple(f(n.end), 1, SynthEvent.NoteOff(f(n.end), ch, n.pitch))
            }
            for (b in track.bends) out += Triple(f(b.time), 2, SynthEvent.PitchBend(f(b.time), ch, (b.value + 8192).coerceIn(0, 16383)))
        }
        return out.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
    }

    /** 악보 전체를 렌더 (마스터링 전, 인터리브 스테레오 float) */
    fun render(renderer: OfflineRenderer, score: Score): FloatArray {
        val total = ((score.durationSec + TAIL_SEC) * renderer.sampleRate).roundToInt()
        return renderer.render(events(score, renderer.sampleRate), total)
    }
}
