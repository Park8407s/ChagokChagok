package com.kumhoht.todaysost.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.kumhoht.todaysost.BuildConfig
import com.kumhoht.todaysost.core.model.BarMode
import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.engine.synth.SynthSmokeTest
import com.kumhoht.todaysost.ui.components.OstStatusBar
import com.kumhoht.todaysost.ui.components.Screen
import com.kumhoht.todaysost.ui.model.SampleData
import com.kumhoht.todaysost.ui.model.TrackUi
import com.kumhoht.todaysost.ui.screens.AlbumScreen
import com.kumhoht.todaysost.ui.screens.ArrivalScreen
import com.kumhoht.todaysost.ui.screens.DemoScreen
import com.kumhoht.todaysost.ui.screens.Drive
import com.kumhoht.todaysost.ui.screens.HomeScreen
import com.kumhoht.todaysost.ui.screens.SettingsScreen
import com.kumhoht.todaysost.ui.theme.Ost
import com.kumhoht.todaysost.ui.theme.OstTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 화면 전환과 상태 (임시: 시안의 예시 데이터로 그린다).
 * 실제 주행 기록·작곡 엔진·재생이 붙으면 ViewModel 로 옮긴다.
 *
 * adb 로 화면 바로 열기 (디버그 빌드):
 *   adb shell am start -n com.kumhoht.todaysost/.ui.MainActivity --es screen ARRIVAL --es drive ROUGH
 *   adb shell am start -n com.kumhoht.todaysost/.ui.MainActivity --ez synthTest true
 */
class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(Screen.HOME)
    private var genre by mutableStateOf(Genre.US_POP)
    private var barMode by mutableStateOf(BarMode.NORMAL)
    private var demoDrive by mutableStateOf(Drive.SMOOTH)
    private var demoSample by mutableStateOf<Pair<Genre, Drive>?>(Genre.US_POP to Drive.SMOOTH)
    private var arrival by mutableStateOf<TrackUi>(SampleData.smoothTrack())
    private var synthStatus by mutableStateOf<String?>(null)
    private var synthRunning by mutableStateOf(false)
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                onReplay = ::notReady, onOpenAlbum = { screen = Screen.ALBUM },
                            )
                            Screen.ARRIVAL -> ArrivalScreen(
                                track = arrival, progress = 0f, showDemoEntry = true,
                                onPlay = ::notReady, onShare = ::notReady, onOpenDemo = { screen = Screen.DEMO },
                            )
                            Screen.ALBUM -> AlbumScreen(SampleData.days, SampleData.month, onPlayMonth = ::notReady)
                            Screen.DEMO -> DemoScreen(
                                smooth = SampleData.smoothTrack(), rough = SampleData.roughTrack(),
                                drive = demoDrive, onDrive = { demoDrive = it },
                                sample = demoSample, onSample = { g, d -> demoSample = g to d; notReady() },
                                onMakeNow = { showArrival(demoSample?.first ?: genre, demoDrive) },
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
        player?.release()
        super.onDestroy()
    }

    private fun showArrival(g: Genre, d: Drive) {
        arrival = if (d == Drive.SMOOTH) SampleData.smoothTrack(g) else SampleData.roughTrack(g)
        screen = Screen.ARRIVAL
    }

    /** 작곡 엔진(M2) 전이라 재생·공유는 아직 없다 */
    private fun notReady() {
        Toast.makeText(this, "작곡 엔진을 붙인 뒤 재생할 수 있어요", Toast.LENGTH_SHORT).show()
    }

    private fun handleDebugIntent() {
        if (!BuildConfig.DEBUG) return
        intent.getStringExtra("screen")?.let { name -> Screen.entries.firstOrNull { it.name == name }?.let { screen = it } }
        intent.getStringExtra("drive")?.let { name ->
            Drive.entries.firstOrNull { it.name == name }?.let { demoDrive = it; showArrival(genre, it) }
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
                android.util.Log.e("SynthSmoke", "실패", t)
                "실패: ${t.message}"
            }
            synthRunning = false
        }
    }

    private fun play(file: File) {
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }
}
