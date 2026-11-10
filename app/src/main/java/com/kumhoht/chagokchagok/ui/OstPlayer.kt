package com.kumhoht.chagokchagok.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * 완성된 트랙 재생. 미디어 오디오 포커스를 요청하고,
 * 내비 음성 안내처럼 잠깐 끼어드는 소리는 시스템이 음악 소리를 줄인다(ducking).
 * 포커스를 완전히 잃으면 멈춘다.
 *
 * 미디어 세션: 차량 미디어 위젯·미디어 센터에 지금 곡 제목이 뜨고,
 * 위젯과 핸들 버튼으로 재생/일시정지/다음 곡/멈춤을 할 수 있다 ([com.kumhoht.chagokchagok.OstMediaService] 가 세션을 차량에 알림).
 */
class OstPlayer(private val context: Context) {
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
    private var onDone: () -> Unit = {}

    /**
     * MediaPlayer 는 여러 스레드에서 동시에 쓰면 안 된다 (IllegalStateException 으로 앱이 죽음).
     * 기록 서비스는 백그라운드 스레드에서 play/stop 을 부르고 화면은 메인 스레드에서 진행률을 읽으므로,
     * 모든 조작을 메인 스레드 한 곳으로 모은다.
     */
    private val main = Handler(Looper.getMainLooper())
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /** 차량 미디어 위젯·핸들 버튼과 연결되는 미디어 세션 */
    val session: MediaSession = MediaSession(context, "ChagokChagok").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = resume()
            override fun onPause() = pause()
            override fun onStop() = stop()
            override fun onSkipToNext() = onMain { if (player != null) finish() }
        }, main)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            setSessionActivity(
                android.app.PendingIntent.getActivity(
                    context, 0, launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        setPlaybackState(state(PlaybackState.STATE_NONE))
    }

    /** 지금 재생 중인 파일 (일시정지 중이면 null → 화면 버튼은 '멈춤' 상태) */
    @Volatile var current: File? = null
        private set
    private var pausedFile: File? = null

    /**
     * @param title 미디어 위젯에 보일 곡 제목 (없으면 파일 이름으로 만든다)
     * @param subtitle 둘째 줄 (장르·길이 등)
     * @param onDone 끝까지 듣거나 '다음 곡'을 누르면 불린다 (월간 OST 이어 듣기)
     */
    fun play(file: File, title: String? = null, subtitle: String? = null, onDone: () -> Unit = {}) = onMain {
        stopNow()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return@onMain
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(file.absolutePath)
                setOnCompletionListener { finish() }
                prepare()
                start()
            }
            player = mp
            current = file
            this.onDone = onDone
            val (t, s) = describe(file, title, subtitle)
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, t)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, s)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, "차곡차곡")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, mp.duration.toLong())
                    .build(),
            )
            session.isActive = true
            session.setPlaybackState(state(PlaybackState.STATE_PLAYING))
        } catch (t: Throwable) {
            android.util.Log.e("OstPlayer", "재생 실패: $file", t)
            stopNow()
        }
    }

    fun stop() = onMain { stopNow() }

    /** 위젯·핸들의 일시정지 */
    private fun pause() = onMain {
        val mp = player ?: return@onMain
        if (!mp.isPlaying) return@onMain
        mp.pause()
        pausedFile = current
        current = null
        session.setPlaybackState(state(PlaybackState.STATE_PAUSED))
    }

    /** 위젯·핸들의 재생 (일시정지한 곳부터) */
    private fun resume() = onMain {
        val mp = player ?: return@onMain
        val f = pausedFile ?: return@onMain
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return@onMain
        mp.start()
        current = f
        pausedFile = null
        session.setPlaybackState(state(PlaybackState.STATE_PLAYING))
    }

    /** 곡이 끝났거나 '다음 곡': 정리하고 다음 할 일을 부른다 */
    private fun finish() {
        val next = onDone
        stopNow()
        next()
    }

    private fun stopNow() {
        player?.release()
        player = null
        current = null
        pausedFile = null
        onDone = {}
        audio.abandonAudioFocusRequest(focus)
        session.setPlaybackState(state(PlaybackState.STATE_STOPPED))
    }

    private fun state(s: Int): PlaybackState {
        val pos = runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT
        return PlaybackState.Builder()
            .setActions(actions)
            .setState(s, pos, if (s == PlaybackState.STATE_PLAYING) 1f else 0f)
            .build()
    }

    /** 위젯에 보일 제목·둘째 줄. 넘겨받은 게 없으면 파일 이름에서 만든다 (내장 샘플: pop_A_smooth.mp3 등) */
    private fun describe(file: File, title: String?, subtitle: String?): Pair<String, String> {
        if (title != null) return title to (subtitle ?: "차곡차곡")
        val m = Regex("""(pop|ballad|kids|trot|dance)_(A_smooth|B_rough)""").find(file.name)
        if (m != null) {
            val genre = mapOf("pop" to "팝", "ballad" to "발라드", "kids" to "동요", "trot" to "트로트", "dance" to "댄스")[m.groupValues[1]]
            val drive = if (m.groupValues[2].startsWith("A")) "A 부드러운 운전" else "B 급한 운전"
            return "가상 주행 샘플 · $genre" to "$drive · 같은 출근길 15.0km"
        }
        return "오늘의 OST" to (subtitle ?: "차곡차곡")
    }

    /** 0..1 (메인 스레드에서 읽는다. 해제 중인 플레이어를 만나도 죽지 않게 0 으로) */
    val progress: Float
        get() = player?.let { runCatching { if (it.duration > 0) it.currentPosition.toFloat() / it.duration else 0f }.getOrDefault(0f) } ?: 0f
}
