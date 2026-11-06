package com.kumhoht.todaysost.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * 완성된 트랙(WAV) 재생. 미디어 오디오 포커스를 요청하고,
 * 내비 음성 안내처럼 잠깐 끼어드는 소리는 시스템이 음악 소리를 줄인다(ducking).
 * 포커스를 완전히 잃으면 멈춘다.
 * 정식 재생(백그라운드·Media3 ExoPlayer·미디어 세션)은 M3 에서 바꾼다.
 */
class OstPlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { change ->
            // M1-3 확인용 로그: 내비 안내가 끼어들 때 어떤 포커스 변화가 오는지 (adb logcat -s OstPlayer)
            android.util.Log.i("OstPlayer", "audio focus → " + when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
                AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
                else -> "$change"
            })
            if (change == AudioManager.AUDIOFOCUS_LOSS) stop()
        }
        .build()
    private var player: MediaPlayer? = null

    /**
     * MediaPlayer 는 여러 스레드에서 동시에 쓰면 안 된다 (IllegalStateException 으로 앱이 죽음).
     * 기록 서비스는 백그라운드 스레드에서 play/stop 을 부르고 화면은 메인 스레드에서 진행률을 읽으므로,
     * 모든 조작을 메인 스레드 한 곳으로 모은다.
     */
    private val main = Handler(Looper.getMainLooper())
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /** 지금 재생 중인 파일 */
    @Volatile var current: File? = null
        private set

    fun play(file: File, onDone: () -> Unit = {}) = onMain {
        stopNow()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return@onMain
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopNow(); onDone() }
                prepare()
                start()
            }
            current = file
        } catch (t: Throwable) {
            android.util.Log.e("OstPlayer", "재생 실패: $file", t)
            stopNow()
        }
    }

    fun stop() = onMain { stopNow() }

    private fun stopNow() {
        player?.release()
        player = null
        current = null
        audio.abandonAudioFocusRequest(focus)
    }

    /** 0..1 (메인 스레드에서 읽는다. 해제 중인 플레이어를 만나도 죽지 않게 0 으로) */
    val progress: Float
        get() = player?.let { runCatching { if (it.duration > 0) it.currentPosition.toFloat() / it.duration else 0f }.getOrDefault(0f) } ?: 0f
}
