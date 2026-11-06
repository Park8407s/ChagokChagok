package com.kumhoht.todaysost.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
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

    /** 지금 재생 중인 파일 */
    var current: File? = null
        private set

    fun play(file: File, onDone: () -> Unit = {}) {
        stop()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        current = file
        player = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(file.absolutePath)
            setOnCompletionListener { stop(); onDone() }
            prepare()
            start()
        }
    }

    fun stop() {
        player?.release()
        player = null
        current = null
        audio.abandonAudioFocusRequest(focus)
    }

    val isPlaying: Boolean get() = player?.isPlaying == true

    /** 0..1 */
    val progress: Float
        get() = player?.let { if (it.duration > 0) it.currentPosition.toFloat() / it.duration else 0f } ?: 0f
}
