package com.kumhoht.todaysost.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.kumhoht.todaysost.BuildConfig
import com.kumhoht.todaysost.core.model.BarMode
import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.engine.TrackMaker
import com.kumhoht.todaysost.engine.composer.Scenarios
import com.kumhoht.todaysost.engine.synth.SynthSmokeTest
import com.kumhoht.todaysost.ui.components.OstStatusBar
import com.kumhoht.todaysost.ui.components.Screen
import com.kumhoht.todaysost.ui.model.SampleData
import com.kumhoht.todaysost.ui.model.TrackUi
import com.kumhoht.todaysost.ui.model.TrackUiFactory
import com.kumhoht.todaysost.ui.screens.AlbumScreen
import com.kumhoht.todaysost.ui.screens.ArrivalScreen
import com.kumhoht.todaysost.ui.screens.DemoScreen
import com.kumhoht.todaysost.ui.screens.Drive
import com.kumhoht.todaysost.ui.screens.HomeScreen
import com.kumhoht.todaysost.ui.screens.SettingsScreen
import com.kumhoht.todaysost.ui.theme.Ost
import com.kumhoht.todaysost.ui.theme.OstTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 화면 전환과 상태.
 * - 가상 주행(데모) 트랙은 실제 작곡 엔진(drive_music_v3 포팅)으로 만들어 재생한다.
 * - 앨범·어제의 OST 목록은 아직 시안의 예시 데이터 (저장소 Room 연결 전).
 *
 * adb 로 바로 열기 (디버그 빌드):
 *   adb shell am start -n com.kumhoht.todaysost/.ui.MainActivity --es screen DEMO
 *   adb shell am start -n com.kumhoht.todaysost/.ui.MainActivity --es make US_POP --es drive ROUGH   (곡 만들고 도착 화면)
 *   adb shell am start -n com.kumhoht.todaysost/.ui.MainActivity --ez synthTest true
 */
class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(Screen.HOME)
    private var genre by mutableStateOf(Genre.US_POP)
    private var barMode by mutableStateOf(BarMode.NORMAL)
    private var demoDrive by mutableStateOf(Drive.SMOOTH)
    private var demoSample by mutableStateOf<Pair<Genre, Drive>?>(null)
    private var arrival by mutableStateOf(trackUi(Genre.US_POP, Drive.SMOOTH))
    private var arrivalFile by mutableStateOf<File?>(null)
    private var playingFile by mutableStateOf<File?>(null)
    private var progress by mutableFloatStateOf(0f)
    private var synthStatus by mutableStateOf<String?>(null)
    private var synthRunning by mutableStateOf(false)
    private var making: Job? = null

    private lateinit var player: OstPlayer
    private val maker by lazy { TrackMaker.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = OstPlayer(this)
        lifecycleScope.launch { runCatching { maker.warmUp() }.onFailure { Log.e(TAG, "사운드폰트 로드 실패", it) } }
        lifecycleScope.launch { while (isActive) { progress = player.progress; delay(200) } }
        handleDebugIntent()
        setContent {
            OstTheme {
                Column(Modifier.fillMaxSize().background(Ost.Bg)) {
                    OstStatusBar(screen) { screen = it }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                selected = genre, onSelect = { genre = it },
                                yesterday = SampleData.yesterday, yesterdayTrackCount = SampleData.yesterdayTrackCount,
                                // 어제의 OST 예시(발라드 30마디)를 부드러운 운전 기록으로 만들어 들려준다
                                onReplay = { makeAndPlay(Genre.POP_BALLAD, Drive.SMOOTH) }, onOpenAlbum = { screen = Screen.ALBUM },
                            )
                            Screen.ARRIVAL -> ArrivalScreen(
                                track = arrival,
                                progress = if (playingFile != null && playingFile == arrivalFile) progress else 0f,
                                showDemoEntry = true,
                                playing = playingFile != null && playingFile == arrivalFile,
                                onPlay = ::toggleArrival, onShare = ::notReady, onOpenDemo = { screen = Screen.DEMO },
                            )
                            Screen.ALBUM -> AlbumScreen(SampleData.days, SampleData.month, onPlayMonth = ::notReady)
                            Screen.DEMO -> DemoScreen(
                                smooth = trackUi(genre, Drive.SMOOTH), rough = trackUi(genre, Drive.ROUGH),
                                drive = demoDrive, onDrive = { demoDrive = it },
                                sample = demoSample,
                                onSample = { g, d -> demoSample = g to d; makeAndPlay(g, d) },
                                onMakeNow = { makeArrival(demoSample?.first ?: genre, demoDrive) },
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                barMode = barMode, onBarMode = { barMode = it },
                                onSynthTest = if (BuildConfig.DEBUG && !synthRunning) ::runSynthTest else null,
                                synthStatus = synthStatus,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        player.stop()
        super.onDestroy()
    }

    private fun bars(d: Drive) = if (d == Drive.SMOOTH) Scenarios.SMOOTH else Scenarios.ROUGH
    private fun key(g: Genre, d: Drive) = "demo_${g.name.lowercase()}_${d.name.lowercase()}"

    private fun trackUi(g: Genre, d: Drive, seconds: Double = com.kumhoht.todaysost.core.model.TrackRules.songSeconds(30, g)): TrackUi =
        TrackUiFactory.from(
            id = 1000L + g.ordinal * 10 + d.ordinal, number = 1, title = "11월 4일 출근길", genre = g,
            bars = bars(d), seconds = seconds, distanceKm = 15.0, driveMinutes = 17,
        )

    /** 데모 샘플: 만들어서 바로 재생 (이미 재생 중인 같은 곡이면 멈춤) */
    private fun makeAndPlay(g: Genre, d: Drive) {
        val target = File(filesDir, "tracks/${key(g, d)}.wav")
        if (playingFile == target) { stopPlayback(); return }
        make(g, d) { file -> play(file) }
    }

    /** 가상 주행으로 지금 만들기 → 도착 화면 + 자동 재생 */
    private fun makeArrival(g: Genre, d: Drive) = make(g, d) { file ->
        arrival = trackUi(g, d)
        arrivalFile = file
        screen = Screen.ARRIVAL
        play(file)
    }

    private fun make(g: Genre, d: Drive, then: (File) -> Unit) {
        if (making?.isActive == true) { toast("곡을 만드는 중이에요"); return }
        stopPlayback()
        val cached = File(filesDir, "tracks/${key(g, d)}.wav").exists()
        if (!cached) toast("주행 기록으로 곡을 만드는 중이에요")
        making = lifecycleScope.launch {
            try {
                val made = maker.make(g, bars(d), key(g, d))
                then(made.file)
            } catch (t: Throwable) {
                Log.e(TAG, "곡 만들기 실패", t)
                toast("곡을 만들지 못했어요: ${t.message}")
            }
        }
    }

    private fun toggleArrival() {
        val f = arrivalFile
        when {
            f == null -> makeArrival(arrival.genre, if (arrival.roughEvents > 0) Drive.ROUGH else Drive.SMOOTH)
            playingFile == f -> stopPlayback()
            else -> play(f)
        }
    }

    private fun play(file: File) {
        player.play(file) { playingFile = null }
        playingFile = player.current
    }

    private fun stopPlayback() { player.stop(); playingFile = null }

    private fun notReady() = toast("이 기능은 아직 준비 중이에요")

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun handleDebugIntent() {
        if (!BuildConfig.DEBUG) return
        intent.getStringExtra("screen")?.let { name -> Screen.entries.firstOrNull { it.name == name }?.let { screen = it } }
        val d = intent.getStringExtra("drive")?.let { name -> Drive.entries.firstOrNull { it.name == name } }
        if (d != null) { demoDrive = d; arrival = trackUi(genre, d); screen = Screen.ARRIVAL }
        intent.getStringExtra("make")?.let { name ->
            Genre.entries.firstOrNull { it.name == name }?.let { g -> genre = g; makeArrival(g, d ?: Drive.SMOOTH) }
        }
        if (intent.getBooleanExtra("synthTest", false)) { screen = Screen.SETTINGS; runSynthTest() }
    }

    private fun runSynthTest() {
        synthRunning = true
        synthStatus = "렌더링 중..."
        lifecycleScope.launch {
            synthStatus = try {
                val result = withContext(Dispatchers.Default) { SynthSmokeTest.run(applicationContext) }
                play(result.file)
                result.summary()
            } catch (t: Throwable) {
                Log.e("SynthSmoke", "실패", t)
                "실패: ${t.message}"
            }
            synthRunning = false
        }
    }

    private companion object { const val TAG = "OstMain" }
}
