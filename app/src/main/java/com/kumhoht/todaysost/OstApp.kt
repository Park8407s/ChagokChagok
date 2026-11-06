package com.kumhoht.todaysost

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.kumhoht.todaysost.core.model.BarMode
import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.data.OstDatabase
import com.kumhoht.todaysost.engine.TrackMaker
import com.kumhoht.todaysost.engine.session.Gear
import com.kumhoht.todaysost.ui.OstPlayer
import com.kumhoht.todaysost.vehicle.LiveVehicle
import com.kumhoht.todaysost.vehicle.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 앱 전체에서 하나씩만 쓰는 객체 */
class OstApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var db: OstDatabase private set
    lateinit var telemetry: Telemetry private set
    lateinit var player: OstPlayer private set
    val maker: TrackMaker by lazy { TrackMaker.get(this) }
    val prefs by lazy { getSharedPreferences("ost", MODE_PRIVATE) }

    /** 주행 기록기 상태 (화면 표시용) */
    private val _recording = MutableStateFlow(RecorderState())
    val recording: StateFlow<RecorderState> = _recording.asStateFlow()
    fun setRecording(s: RecorderState) { _recording.value = s }

    var genre: Genre
        get() = Genre.entries.firstOrNull { it.name == prefs.getString("genre", null) } ?: Genre.US_POP
        set(v) = prefs.edit().putString("genre", v.name).apply()

    var barMode: BarMode
        get() = BarMode.entries.firstOrNull { it.name == prefs.getString("barMode", null) } ?: BarMode.NORMAL
        set(v) = prefs.edit().putString("barMode", v.name).apply()

    override fun onCreate() {
        super.onCreate()
        db = OstDatabase.create(this)
        telemetry = Telemetry(this).also { it.start() }
        player = OstPlayer(this)
        if (BuildConfig.DEBUG) registerDebugSim()
    }

    /**
     * 디버그 빌드 전용: adb 로 차량 신호 흉내 (Virtual Console 없이 기록기를 시험할 때)
     *   adb shell am broadcast -a com.kumhoht.todaysost.SIM --es gear D --ef speed 60 [--es turn R] [--ei limit 50]
     *   adb shell am broadcast -a com.kumhoht.todaysost.SIM --ez clear true   (실제 신호로 돌아가기)
     *   adb shell am broadcast -a com.kumhoht.todaysost.SIM --ez navVoice true (내비 안내처럼 2초 소리: 오디오 포커스 확인)
     *   adb shell am broadcast -a com.kumhoht.todaysost.SIM --ez playDemo true (만들어 둔 트랙 재생: 주행 중 재생 확인)
     */
    private fun registerDebugSim() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getBooleanExtra("navVoice", false)) { DebugAudio.navGuidanceBeep(this@OstApp); return }
                if (i.getBooleanExtra("playDemo", false)) {
                    val f = java.io.File(filesDir, "tracks").listFiles()?.firstOrNull { it.name.endsWith(".wav") }
                    Log.i("DebugSim", "playDemo $f"); f?.let { player.play(it) }; return
                }
                if (i.getBooleanExtra("clear", false)) { telemetry.override = null; Log.i("DebugSim", "clear"); return }
                val base = telemetry.override ?: telemetry.live.value
                val v = LiveVehicle(
                    gear = i.getStringExtra("gear")?.let { Gear.valueOf(it) } ?: base.gear,
                    speedKmh = if (i.hasExtra("speed")) i.getFloatExtra("speed", 0f) else base.speedKmh,
                    turn = if (i.hasExtra("turn")) i.getStringExtra("turn")?.firstOrNull()?.takeIf { it == 'L' || it == 'R' } else base.turn,
                    speedLimit = if (i.hasExtra("limit")) i.getIntExtra("limit", 0).takeIf { it > 0 } else base.speedLimit,
                )
                telemetry.override = v
                Log.i("DebugSim", "$v")
            }
        }
        ContextCompat.registerReceiver(this, r, IntentFilter("com.kumhoht.todaysost.SIM"), ContextCompat.RECEIVER_EXPORTED)
    }

    companion object {
        fun of(c: Context) = c.applicationContext as OstApp
    }
}

data class RecorderState(
    val recording: Boolean = false,
    val seconds: Int = 0,
    /** 2분 미만이라 트랙을 만들지 않은 마지막 운행 (화면 안내용). 안내를 닫으면 null */
    val shortDrive: ShortDrive? = null,
    /** 곡을 만드는 중 */
    val composing: Boolean = false,
)

/** @param seconds 운행 시간, @param endedAt 도착 시각(ms) */
data class ShortDrive(val seconds: Int, val endedAt: Long)
