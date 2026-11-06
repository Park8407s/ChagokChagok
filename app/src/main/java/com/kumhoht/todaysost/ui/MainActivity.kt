package com.kumhoht.todaysost.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kumhoht.todaysost.BuildConfig
import com.kumhoht.todaysost.R
import com.kumhoht.todaysost.engine.synth.SynthSmokeTest
import com.kumhoht.todaysost.ui.theme.Ost
import com.kumhoht.todaysost.ui.theme.OstTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 홈 화면 (임시). 도착 화면·앨범·가상 주행 샘플은 M3 이후에 붙인다.
 * 디버그 빌드에는 M1-1 확인용 '소리 엔진 테스트' 버튼이 있다.
 *   adb 로 바로 실행: adb shell am start -n com.kumhoht.todaysost/.ui.MainActivity --ez synthTest true
 */
class MainActivity : ComponentActivity() {
    private var status by mutableStateOf<String?>(null)
    private var running by mutableStateOf(false)
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OstTheme {
                HomePlaceholder(
                    debugStatus = status,
                    onSynthTest = if (BuildConfig.DEBUG && !running) ::runSynthTest else null,
                )
            }
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra("synthTest", false)) runSynthTest()
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }

    private fun runSynthTest() {
        running = true
        status = "렌더링 중..."
        lifecycleScope.launch {
            val text = try {
                val result = withContext(Dispatchers.Default) { SynthSmokeTest.run(applicationContext) }
                play(result.file)
                result.summary()
            } catch (t: Throwable) {
                android.util.Log.e("SynthSmoke", "실패", t)
                "실패: ${t.message}"
            }
            status = text
            running = false
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

@Composable
fun HomePlaceholder(debugStatus: String? = null, onSynthTest: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().background(Ost.Night),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("♫", style = MaterialTheme.typography.displaySmall, color = Ost.Spot)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge, color = Ost.TextSoft)
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = { onSynthTest?.invoke() }, enabled = onSynthTest != null) {
                Text("소리 엔진 테스트 (개발용)", style = MaterialTheme.typography.titleMedium)
            }
            debugStatus?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Ost.TextSoft, textAlign = TextAlign.Center)
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomePlaceholderPreview() {
    OstTheme { HomePlaceholder() }
}
