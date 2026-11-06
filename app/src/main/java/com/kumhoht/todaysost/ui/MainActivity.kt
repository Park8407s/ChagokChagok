package com.kumhoht.todaysost.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kumhoht.todaysost.BuildConfig
import com.kumhoht.todaysost.OstApp
import com.kumhoht.todaysost.core.model.BarMode
import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.core.model.TrackRules
import com.kumhoht.todaysost.data.TrackEntity
import com.kumhoht.todaysost.engine.composer.Scenarios
import com.kumhoht.todaysost.engine.synth.SynthSmokeTest
import com.kumhoht.todaysost.recorder.DriveService
import com.kumhoht.todaysost.ui.components.OstStatusBar
import com.kumhoht.todaysost.ui.components.Screen
import com.kumhoht.todaysost.ui.model.AlbumMapper
import com.kumhoht.todaysost.ui.model.PlayState
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
import java.util.Calendar

/**
 * 화면 전환과 상태.
 * - 실제 운전: DriveService 가 도착 시 곡을 만들어 저장·재생하고 이 화면을 도착 화면으로 연다
 * - 앨범·어제의 OST: 저장된 트랙 (아직 없으면 시안의 예시 데이터)
 * - 데모: 가상 주행 시나리오로 그 자리에서 곡을 만든다
 *
 * adb (디버그 빌드):
 *   adb shell am start -n com.kumhoht.todaysost/.ui.MainActivity --es screen DEMO
 *   adb shell am start -n com.kumhoht.todaysost/.ui.MainActivity --es make US_POP --es drive ROUGH
 */
class MainActivity : ComponentActivity() {
    private val app by lazy { OstApp.of(this) }
    private val player get() = app.player

    private var screen by mutableStateOf(Screen.HOME)
    private var genre by mutableStateOf(Genre.US_POP)
    private var barMode by mutableStateOf(BarMode.NORMAL)
    private var demoDrive by mutableStateOf(Drive.SMOOTH)
    private var demoSample by mutableStateOf<Pair<Genre, Drive>?>(null)
    private var arrival by mutableStateOf(demoUi(Genre.US_POP, Drive.SMOOTH))
    private var arrivalFile by mutableStateOf<File?>(null)
    private var playingFile by mutableStateOf<File?>(null)
    private var progress by mutableFloatStateOf(0f)
    private var tracks by mutableStateOf<List<TrackEntity>>(emptyList())
    private var synthStatus by mutableStateOf<String?>(null)
    private var synthRunning by mutableStateOf(false)
    private var making: Job? = null
    /** 지금 만들고 있는 데모 곡 (버튼에 '만드는 중…' 표시) */
    private var makingKey by mutableStateOf<String?>(null)

    /** 지나온 화면 (‹ 이전·뒤로 가기용). 홈으로 가면 비운다 */
    private val backStack = androidx.compose.runtime.mutableStateListOf<Screen>()
    /** 최소 운행 시간 미만 안내 */
    private var shortDrive by mutableStateOf<com.kumhoht.todaysost.ShortDrive?>(null)
    /** 도착 화면에 보여 줄 곡이 있는지 ('방금 곡' 버튼) */
    private var hasArrival by mutableStateOf(false)

    private fun go(to: Screen) {
        if (to == screen) return
        if (to == Screen.ARRIVAL) hasArrival = true
        if (to == Screen.HOME) backStack.clear() else backStack.add(screen)
        screen = to
    }

    private fun back() {
        screen = backStack.removeLastOrNull() ?: Screen.HOME
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) app.telemetry.onPermissionsGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        genre = app.genre
        barMode = app.barMode
        requestRuntimePermissions()
        DriveService.start(this)
        lifecycleScope.launch { runCatching { app.maker.warmUp() }.onFailure { Log.e(TAG, "사운드폰트 로드 실패", it) } }
        lifecycleScope.launch {
            while (isActive) {
                progress = player.progress
                playingFile = player.current
                delay(200)
            }
        }
        lifecycleScope.launch { app.db.tracks().all().collect { tracks = it } }
        // 최소 운행 시간 미만으로 도착: 홈으로 와서 안내 카드를 띄운다 (30분 지난 안내는 버림)
        lifecycleScope.launch {
            app.recording.collect { st ->
                val sd = st.shortDrive?.takeIf { System.currentTimeMillis() - it.endedAt < 30 * 60_000L }
                if (sd != null && sd != shortDrive) go(Screen.HOME)
                shortDrive = sd
            }
        }
        handleIntent(intent)
        // 시스템 뒤로 가기(제스처·키)도 이전 화면으로. 더 갈 곳이 없으면 기본 동작(앱 나가기)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backStack.isNotEmpty() || screen != Screen.HOME) back()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
        setContent {
            OstTheme {
                Column(Modifier.fillMaxSize().background(Ost.Bg)) {
                    OstStatusBar(screen, canGoBack = backStack.isNotEmpty(), showArrival = hasArrival, onBack = ::back) { go(it) }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        Content()
                        shortDrive?.let { sd ->
                            androidx.compose.runtime.LaunchedEffect(sd) { delay(20_000); dismissShortDrive() } // 20초 뒤 자동으로 닫힘
                            com.kumhoht.todaysost.ui.components.ShortDriveNotice(
                                seconds = sd.seconds, onClose = ::dismissShortDrive,
                                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 40.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @androidx.compose.runtime.Composable
    private fun Content() {
        val saved = tracks
        when (screen) {
            Screen.HOME -> {
                val yesterday = saved.firstOrNull { it.dayKey != DriveService.dayKey(System.currentTimeMillis()) }
                HomeScreen(
                    selected = genre, onSelect = { genre = it; app.genre = it },
                    yesterday = yesterday?.let(AlbumMapper::trackUi) ?: SampleData.yesterday,
                    yesterdayTrackCount = yesterday?.let { y -> saved.count { it.dayKey == y.dayKey } } ?: SampleData.yesterdayTrackCount,
                    onReplay = { if (yesterday != null) toggle(File(yesterday.filePath)) else makeAndPlay(Genre.POP_BALLAD, Drive.SMOOTH) },
                    onOpenAlbum = { go(Screen.ALBUM) },
                    replayState = if (yesterday != null) stateOf(File(yesterday.filePath))
                    else stateOf(demoFile(Genre.POP_BALLAD, Drive.SMOOTH), key(Genre.POP_BALLAD, Drive.SMOOTH)),
                )
            }
            Screen.ARRIVAL -> ArrivalScreen(
                track = arrival,
                progress = if (playingFile != null && playingFile == arrivalFile) progress else 0f,
                showDemoEntry = true,
                playing = playingFile != null && playingFile == arrivalFile,
                onPlay = ::toggleArrival, onShare = ::notReady, onOpenDemo = { go(Screen.DEMO) },
            )
            Screen.ALBUM -> {
                val month = Calendar.getInstance().get(Calendar.MONTH) + 1
                if (saved.isEmpty()) AlbumScreen(SampleData.days, SampleData.month, onPlayMonth = ::notReady)
                else AlbumScreen(AlbumMapper.days(saved), AlbumMapper.month(saved, month), onPlayMonth = ::notReady, onOpenTrack = { id -> lifecycleScope.launch { showSaved(id) } })
            }
            Screen.DEMO -> DemoScreen(
                smooth = demoUi(genre, Drive.SMOOTH), rough = demoUi(genre, Drive.ROUGH),
                drive = demoDrive, onDrive = { demoDrive = it },
                sample = demoSample,
                onSample = { g, d -> demoSample = g to d; makeAndPlay(g, d) },
                onMakeNow = { makeArrival(demoSample?.first ?: genre, demoDrive) },
                sampleState = { g, d -> stateOf(demoFile(g, d), key(g, d)) },
            )
            Screen.SETTINGS -> SettingsScreen(
                barMode = barMode, onBarMode = { barMode = it; app.barMode = it },
                onSynthTest = if (BuildConfig.DEBUG && !synthRunning) ::runSynthTest else null,
                synthStatus = synthStatus,
                trackCount = tracks.size,
                onReset = ::resetData,
                onShutdown = { DriveService.shutdown(this) },
            )
        }
    }

    /** 데이터 초기화: 트랙(Room)·곡 파일·설정을 지운다. 사운드폰트 사본은 남긴다. 주차 중에만 */
    private fun resetData() {
        if (app.recording.value.recording) { toast("주차 중에만 초기화할 수 있어요"); return }
        player.stop()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                app.db.tracks().deleteAll()
                File(filesDir, "tracks").deleteRecursively()
                File(filesDir, "smoke").deleteRecursively()
                app.prefs.edit().clear().commit()
            }
            genre = app.genre
            barMode = app.barMode
            arrival = demoUi(genre, Drive.SMOOTH)
            arrivalFile = null
            hasArrival = false
            demoSample = null
            dismissShortDrive()
            backStack.clear()
            screen = Screen.HOME
            toast("데이터를 모두 지웠어요. 처음부터 시작해요.")
        }
    }

    /** 차속(CAR_SPEED)은 위험 권한이라 사용자 허용이 필요하다 */
    private fun requestRuntimePermissions() {
        val wanted = listOf("android.car.permission.CAR_SPEED", Manifest.permission.POST_NOTIFICATIONS)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_TRACK, -1L) ?: -1L
        if (id > 0) lifecycleScope.launch { showSaved(id) }
        if (BuildConfig.DEBUG && intent != null) handleDebugIntent(intent)
    }

    /** 실제 운행으로 만든 트랙을 도착 화면에 띄운다 (재생은 서비스가 이미 시작함) */
    private suspend fun showSaved(id: Long) {
        val t = app.db.tracks().get(id) ?: return
        arrival = AlbumMapper.trackUi(t)
        arrivalFile = File(t.filePath)
        go(Screen.ARRIVAL)
        app.db.tracks().markSeen(id)
    }

    // ---------------------------------------------------------------- 데모 (가상 주행)

    private fun bars(d: Drive) = if (d == Drive.SMOOTH) Scenarios.SMOOTH else Scenarios.ROUGH
    private fun key(g: Genre, d: Drive) = "demo_${g.name.lowercase()}_${d.name.lowercase()}"

    private fun demoUi(g: Genre, d: Drive): TrackUi = TrackUiFactory.from(
        id = 1000L + g.ordinal * 10 + d.ordinal, number = 1, title = "11월 4일 출근길", genre = g,
        bars = bars(d), seconds = TrackRules.songSeconds(30, g), distanceKm = 15.0, driveMinutes = 17,
    )

    private fun makeAndPlay(g: Genre, d: Drive) {
        val target = File(filesDir, "tracks/${key(g, d)}.wav")
        if (playingFile == target) { player.stop(); return }
        make(g, d) { file -> player.play(file) }
    }

    private fun makeArrival(g: Genre, d: Drive) = make(g, d) { file ->
        arrival = demoUi(g, d)
        arrivalFile = file
        go(Screen.ARRIVAL)
        player.play(file)
    }

    private fun make(g: Genre, d: Drive, then: (File) -> Unit) {
        if (making?.isActive == true) { toast("곡을 만드는 중이에요"); return }
        player.stop()
        if (!File(filesDir, "tracks/${key(g, d)}.wav").exists()) toast("주행 기록으로 곡을 만드는 중이에요")
        makingKey = key(g, d)
        making = lifecycleScope.launch {
            try {
                then(app.maker.make(g, bars(d), key(g, d)).file)
            } catch (t: Throwable) {
                Log.e(TAG, "곡 만들기 실패", t)
                toast("곡을 만들지 못했어요: ${t.message}")
            } finally {
                makingKey = null
            }
        }
    }

    /** 이 곡 파일 버튼의 상태 (재생 중 / 만드는 중 / 멈춤) */
    private fun stateOf(file: File?, makeKey: String? = null): PlayState = when {
        file != null && playingFile == file -> PlayState.PLAYING
        makeKey != null && makingKey == makeKey -> PlayState.LOADING
        else -> PlayState.IDLE
    }

    private fun demoFile(g: Genre, d: Drive) = File(filesDir, "tracks/${key(g, d)}.wav")

    private fun toggleArrival() {
        val f = arrivalFile
        if (f == null) makeArrival(arrival.genre, if (arrival.roughEvents > 0) Drive.ROUGH else Drive.SMOOTH) else toggle(f)
    }

    private fun toggle(f: File) { if (playingFile == f) player.stop() else player.play(f) }

    private fun dismissShortDrive() {
        shortDrive = null
        app.setRecording(app.recording.value.copy(shortDrive = null))
    }

    private fun notReady() = toast("이 기능은 아직 준비 중이에요")

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun handleDebugIntent(intent: Intent) {
        intent.getStringExtra("screen")?.let { name -> Screen.entries.firstOrNull { it.name == name }?.let { go(it) } }
        val d = intent.getStringExtra("drive")?.let { name -> Drive.entries.firstOrNull { it.name == name } }
        if (d != null) { demoDrive = d; arrival = demoUi(genre, d); go(Screen.ARRIVAL) }
        intent.getStringExtra("make")?.let { name ->
            Genre.entries.firstOrNull { it.name == name }?.let { g -> genre = g; makeArrival(g, d ?: Drive.SMOOTH) }
        }
        if (intent.getBooleanExtra("synthTest", false)) { go(Screen.SETTINGS); runSynthTest() }
    }

    private fun runSynthTest() {
        synthRunning = true
        synthStatus = "렌더링 중..."
        lifecycleScope.launch {
            synthStatus = try {
                val result = withContext(Dispatchers.Default) { SynthSmokeTest.run(applicationContext) }
                player.play(result.file)
                result.summary()
            } catch (t: Throwable) {
                Log.e("SynthSmoke", "실패", t)
                "실패: ${t.message}"
            }
            synthRunning = false
        }
    }

    companion object {
        private const val TAG = "OstMain"
        const val EXTRA_TRACK = "trackId"
    }
}
