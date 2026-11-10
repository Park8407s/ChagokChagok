package com.kumhoht.chagokchagok.engine.synth

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 앱에 들어 있는 사운드폰트 (assets/soundfonts) */
enum class SoundFont(val asset: String) {
    /** 피아노·기타·베이스·드럼·신스·오르간·색소폰·리코더·오르골 */
    GENERAL_USER("soundfonts/GeneralUser-GS.sf3"),
    /** 스트링·코러스·브라스 */
    FLUID_ENSEMBLE("soundfonts/FluidR3_GM_ensemble.sf3"),
}

/** 렌더 이벤트. [frame] 은 곡 시작부터의 샘플 위치. */
sealed class SynthEvent(val frame: Int) {
    class NoteOn(frame: Int, val channel: Int, val key: Int, val velocity: Int) : SynthEvent(frame)
    class NoteOff(frame: Int, val channel: Int, val key: Int) : SynthEvent(frame)
    /** 채널의 악기를 고른다. 드럼은 bank 128. */
    class Program(frame: Int, val channel: Int, val font: SoundFont, val bank: Int, val program: Int) : SynthEvent(frame)
    class Control(frame: Int, val channel: Int, val controller: Int, val value: Int) : SynthEvent(frame)
    /** 피치 벤드 0..16383 (8192 = 가운데) */
    class PitchBend(frame: Int, val channel: Int, val value: Int) : SynthEvent(frame)
}

/**
 * FluidSynth 오프라인 렌더러.
 * 사운드폰트는 처음 한 번 assets 에서 앱 내부 저장소로 복사한 뒤 파일 경로로 불러온다.
 * 한 인스턴스를 여러 곡에 재사용할 수 있다 (사운드폰트 로드가 가장 오래 걸림). 스레드 하나에서만 쓴다.
 */
class OfflineRenderer(context: Context, val sampleRate: Int = 44_100) : Closeable {
    private var handle: Long = FluidSynthNative.nativeCreate(sampleRate)
    private val fontIds = mutableMapOf<SoundFont, Int>()

    /** 사운드폰트 로드에 걸린 시간(ms) */
    val loadMillis: Long

    init {
        check(handle != 0L) { "FluidSynth 초기화 실패" }
        val t0 = System.currentTimeMillis()
        for (font in SoundFont.entries) {
            val file = copyAsset(context, font.asset)
            val id = FluidSynthNative.nativeLoadSoundFont(handle, file.absolutePath)
            check(id >= 0) { "사운드폰트 로드 실패: ${font.asset}" }
            fontIds[font] = id
        }
        loadMillis = System.currentTimeMillis() - t0
    }

    /** 이벤트를 렌더해 인터리브 스테레오 float(-1..1) 를 돌려준다. */
    fun render(events: List<SynthEvent>, totalFrames: Int): FloatArray {
        check(handle != 0L) { "이미 닫힌 렌더러" }
        val packed = IntArray(events.size * 6)
        events.sortedBy { it.frame }.forEachIndexed { i, e ->
            val o = i * 6
            packed[o] = e.frame
            when (e) {
                is SynthEvent.NoteOn -> { packed[o + 1] = 0; packed[o + 2] = e.channel; packed[o + 3] = e.key; packed[o + 4] = e.velocity }
                is SynthEvent.NoteOff -> { packed[o + 1] = 1; packed[o + 2] = e.channel; packed[o + 3] = e.key }
                is SynthEvent.Program -> {
                    packed[o + 1] = 2; packed[o + 2] = e.channel
                    packed[o + 3] = fontIds.getValue(e.font); packed[o + 4] = e.bank; packed[o + 5] = e.program
                }
                is SynthEvent.Control -> { packed[o + 1] = 3; packed[o + 2] = e.channel; packed[o + 3] = e.controller; packed[o + 4] = e.value }
                is SynthEvent.PitchBend -> { packed[o + 1] = 4; packed[o + 2] = e.channel; packed[o + 3] = e.value }
            }
        }
        return FluidSynthNative.nativeRender(handle, packed, totalFrames) ?: error("렌더 실패 (메모리 부족)")
    }

    override fun close() {
        if (handle != 0L) FluidSynthNative.nativeDestroy(handle)
        handle = 0L
    }

    private fun copyAsset(context: Context, asset: String): File {
        val out = File(context.filesDir, asset)
        val size = context.assets.openFd(asset).use { it.length }
        if (out.length() != size) {
            out.parentFile?.mkdirs()
            val tmp = File(out.path + ".tmp")
            context.assets.open(asset).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(out) || (out.delete() && tmp.renameTo(out))) { "사운드폰트 복사 실패: $asset" }
        }
        return out
    }
}

/** 16bit PCM 스테레오 WAV 로 저장 (-1..1 밖은 잘라냄) */
fun writeWav(file: File, stereo: FloatArray, sampleRate: Int) {
    val dataBytes = stereo.size * 2
    val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
    buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2)
        .putInt(sampleRate).putInt(sampleRate * 4).putShort(4).putShort(16)
    buf.put("data".toByteArray()).putInt(dataBytes)
    for (v in stereo) buf.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
    file.parentFile?.mkdirs()
    RandomAccessFile(file, "rw").use { it.setLength(0); it.write(buf.array()) }
}
