package com.kumhoht.todaysost.engine

import android.content.Context
import android.util.Log
import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.engine.composer.Composer
import com.kumhoht.todaysost.engine.composer.Score
import com.kumhoht.todaysost.engine.session.Bar
import com.kumhoht.todaysost.engine.synth.Mastering
import com.kumhoht.todaysost.engine.synth.OfflineRenderer
import com.kumhoht.todaysost.engine.synth.ScoreRenderer
import com.kumhoht.todaysost.engine.synth.writeWav
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * 곡 만들기 전체 흐름: 마디 → 작곡(악보) → FluidSynth 렌더 → 마스터링 → WAV.
 * FluidSynth 는 스레드 하나에서만 쓰고, 사운드폰트 로드(약 2초)는 처음 한 번만 한다.
 */
class TrackMaker private constructor(private val context: Context) {

    data class Made(val file: File, val score: Score, val seconds: Double, val composeMs: Long, val renderMs: Long, val master: Mastering.Report?)

    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "ost-synth") }.asCoroutineDispatcher()
    private var renderer: OfflineRenderer? = null

    /** 사운드폰트를 미리 올려 둔다 (도착했을 때 기다리지 않도록) */
    suspend fun warmUp() = withContext(dispatcher) { renderer() ; Unit }

    private fun renderer(): OfflineRenderer = renderer ?: OfflineRenderer(context).also {
        renderer = it
        Log.i(TAG, "사운드폰트 로드 ${it.loadMillis}ms")
    }

    /**
     * @param key 같은 key 의 파일이 있으면 다시 만들지 않는다 (예: 데모 샘플 "uspop_A")
     */
    suspend fun make(genre: Genre, bars: List<Bar>, key: String): Made = withContext(dispatcher) {
        val t0 = System.currentTimeMillis()
        val score = Composer.compose(genre, bars)
        val t1 = System.currentTimeMillis()
        val out = File(context.filesDir, "tracks/$key.wav")
        if (out.exists() && out.length() > 44) {
            return@withContext Made(out, score, score.durationSec, t1 - t0, 0, null)
        }
        val r = renderer()
        val pcm = ScoreRenderer.render(r, score)
        val report = Mastering.process(pcm, r.sampleRate, score.durationSec)
        writeWav(out, pcm, r.sampleRate)
        val t2 = System.currentTimeMillis()
        Log.i(TAG, "$key: 음 ${score.noteCount}개, 작곡 ${t1 - t0}ms, 렌더+마스터링 ${t2 - t1}ms, " +
            "곡 %.1f초, 라우드니스 %.1f → %.1f LUFS, 최대 %.1f dBFS".format(score.durationSec, report.inputLufs, report.outputLufs, report.peakDb))
        Made(out, score, score.durationSec, t1 - t0, t2 - t1, report)
    }

    companion object {
        private const val TAG = "TrackMaker"
        @Volatile private var instance: TrackMaker? = null
        fun get(context: Context): TrackMaker =
            instance ?: synchronized(this) { instance ?: TrackMaker(context.applicationContext).also { instance = it } }
    }
}
