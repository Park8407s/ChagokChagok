package com.kumhoht.todaysost

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * 디버그 전용 (M1-3): 내비 음성 안내처럼 끼어드는 소리를 흉내 낸다.
 * USAGE_ASSISTANCE_NAVIGATION_GUIDANCE 로 '잠깐, 다른 소리는 줄여도 됨'(GAIN_TRANSIENT_MAY_DUCK) 포커스를 요청하고
 * 2초짜리 '띵-띵' 소리를 낸다. 이때 음악 쪽(OstPlayer)에 어떤 포커스 변화가 오는지 로그로 본다.
 */
object DebugAudio {
    fun navGuidanceBeep(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
        val granted = audio.requestAudioFocus(focus)
        Log.i(TAG, "nav guidance focus request → ${if (granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "GRANTED" else "result $granted"}")

        val rate = 44_100
        val pcm = ShortArray(rate * 2) { i ->
            val t = i.toDouble() / rate
            val on = (t % 0.5) < 0.35 // 0.35초 소리, 0.15초 쉼
            if (on) (sin(2 * PI * 880 * t) * 12_000).toInt().toShort() else 0
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) {
                t.release()
                audio.abandonAudioFocusRequest(focus)
                Log.i(TAG, "nav guidance done, focus abandoned")
            }
            override fun onPeriodicNotification(t: AudioTrack) {}
        })
        track.play()
    }

    private const val TAG = "DebugAudio"
}
