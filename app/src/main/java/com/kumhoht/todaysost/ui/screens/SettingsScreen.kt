package com.kumhoht.todaysost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kumhoht.todaysost.BuildConfig
import com.kumhoht.todaysost.R
import com.kumhoht.todaysost.core.model.BarMode
import com.kumhoht.todaysost.ui.components.OstIcon
import com.kumhoht.todaysost.ui.components.OutlineButton
import com.kumhoht.todaysost.ui.components.Pill
import com.kumhoht.todaysost.ui.components.PrimaryButton
import com.kumhoht.todaysost.ui.components.card
import com.kumhoht.todaysost.ui.theme.Ost
import com.kumhoht.todaysost.ui.theme.OstType

enum class SettingsMenu(val label: String) {
    COMPOSE("곡 만들기"), ALBUM_SHARE("앨범·공유"), PRIVACY("개인정보"), LICENSES("오픈소스 라이선스"), ABOUT("앱 정보")
}

private class LicenseItem(
    val name: String, val badge: String, val role: String, val author: String, val detail: String,
    /** assets/licenses 안의 전문 파일 (없으면 null) */
    val asset: String?,
)

private val LICENSES = listOf(
    LicenseItem(
        "FluidSynth", "LGPL-2.1", "신디사이저 · 악보를 악기 소리로 연주 · ", "Peter Hanappe 외 기여자",
        "수정 없이 동적 링크(.so)로 사용 · 소스 코드 github.com/FluidSynth/fluidsynth", null,
    ),
    LicenseItem(
        "GeneralUser GS", "GeneralUser GS License v2.0", "기본 악기 소리 (사운드폰트) · ", "© S. Christian Collins",
        "피아노·기타·베이스·드럼·신스 등 · 프로젝트 페이지 schristiancollins.com", "licenses/GeneralUser_GS_LICENSE.txt",
    ),
    LicenseItem(
        "FluidR3_GM", "MIT License", "보조 악기 소리 (사운드폰트) · ", "© Frank Wen",
        "스트링·브라스·코러스 등 · 저작권·허가 문구 전문 포함", "licenses/FluidR3_GM_copyright.txt",
    ),
)

/** 그림 7. 설정 (오픈소스 라이선스는 시안대로, 나머지 메뉴는 1차 범위만) */
@Composable
fun SettingsScreen(
    barMode: BarMode,
    onBarMode: (BarMode) -> Unit,
    onSynthTest: (() -> Unit)?,
    synthStatus: String?,
) {
    var menu by remember { mutableStateOf(SettingsMenu.LICENSES) }
    Row(Modifier.fillMaxSize().padding(start = 64.dp, end = 65.dp, top = 20.dp)) {
        Column(Modifier.width(380.dp)) {
            Row(Modifier.height(61.dp), verticalAlignment = Alignment.CenterVertically) {
                OstIcon(R.drawable.ic_settings, Ost.Text, 46.dp)
                Spacer(Modifier.size(16.dp))
                Text("설정", style = OstType.Display.copy(fontSize = 34.sp))
            }
            Spacer(Modifier.height(30.dp))
            SettingsMenu.entries.forEach { m ->
                val on = m == menu
                Text(
                    m.label,
                    style = OstType.Body.copy(color = if (on) Ost.Text else Ost.TextSub, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                    modifier = Modifier.fillMaxWidth().height(85.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) Ost.CardHigh else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { menu = m }.padding(horizontal = 28.dp, vertical = 23.dp),
                )
            }
        }
        Spacer(Modifier.width(50.dp))
        Column(Modifier.weight(1f)) {
            when (menu) {
                SettingsMenu.LICENSES -> Licenses()
                SettingsMenu.COMPOSE -> ComposeSettings(barMode, onBarMode)
                SettingsMenu.ABOUT -> About(onSynthTest, synthStatus)
                else -> Placeholder(menu.label)
            }
        }
    }
}

@Composable
private fun Licenses() {
    var open by remember { mutableStateOf<LicenseItem?>(null) }
    Text("오픈소스 라이선스", style = OstType.Display)
    Spacer(Modifier.height(25.dp))
    Text(
        "오늘의 OST는 아래 오픈소스 소프트웨어와 악기 소리로 곡을 만듭니다. 라이선스 전문은 앱 안에 들어 있어\n네트워크 없이 볼 수 있어요.",
        style = OstType.Lead.copy(lineHeight = 30.sp),
    )
    Spacer(Modifier.height(32.dp))
    Column(verticalArrangement = Arrangement.spacedBy(21.dp)) {
        LICENSES.forEach { item ->
            Row(Modifier.fillMaxWidth().height(181.dp).card().padding(start = 34.dp, end = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                OstIcon(R.drawable.ic_document, Ost.Yellow, 58.dp)
                Spacer(Modifier.size(30.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, style = OstType.Title)
                        Spacer(Modifier.size(15.dp))
                        Text(
                            item.badge, style = OstType.Label.copy(color = Ost.Text),
                            modifier = Modifier.border(2.dp, Ost.Line, Pill).padding(horizontal = 18.dp, vertical = 5.dp),
                        )
                    }
                    Row {
                        Text(item.role, style = OstType.BodySmall)
                        Text(item.author, style = OstType.BodySmall.copy(color = Ost.TextSub))
                    }
                    Text(item.detail, style = OstType.Label)
                }
                OutlineButton({ open = item }, Modifier.width(182.dp), height = 66.dp, contentPadding = PaddingValues(0.dp)) {
                    Text("전문 보기", style = OstType.BodySmall)
                    OstIcon(R.drawable.ic_chevron_right, Ost.Text, 25.dp)
                }
            }
        }
    }
    Spacer(Modifier.height(21.dp))
    Text("주차 중에만 표시 · 각 라이선스의 저작권·허가 문구를 원문 그대로 고지합니다.", style = OstType.Label)

    open?.let { LicenseDialog(it) { open = null } }
}

@Composable
private fun LicenseDialog(item: LicenseItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember(item) {
        item.asset?.let { runCatching { context.assets.open(it).bufferedReader().readText() }.getOrNull() }
            ?: "${item.name} (${item.badge}) 전문은 다음 업데이트에서 앱에 포함됩니다.\n${item.detail}"
    }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(1200.dp).height(900.dp).card(color = Ost.Bg, border = Ost.Line).padding(40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${item.name} · ${item.badge}", style = OstType.Title, modifier = Modifier.weight(1f))
                PrimaryButton("닫기", onClose, icon = null, height = 66.dp)
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.weight(1f).fillMaxWidth().card().padding(28.dp)) {
                Text(text, style = OstType.Label.copy(color = Ost.Text, lineHeight = 30.sp), modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        }
    }
}

@Composable
private fun ComposeSettings(barMode: BarMode, onBarMode: (BarMode) -> Unit) {
    Text("곡 만들기", style = OstType.Display)
    Spacer(Modifier.height(25.dp))
    Text("마디 길이를 고릅니다. 데모 모드는 짧은 운전으로도 곡을 들어볼 수 있게 한 심사·시연용 설정이에요.", style = OstType.Lead.copy(lineHeight = 30.sp))
    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(21.dp)) {
        BarMode.entries.forEach { mode ->
            val on = mode == barMode
            Column(
                Modifier.weight(1f).height(181.dp).card(border = if (on) Ost.Yellow else null).clickable { onBarMode(mode) }.padding(34.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(mode.label, style = OstType.Title.copy(color = if (on) Ost.Yellow else Ost.Text))
                Text("주행 ${mode.secondsPerBar}초 = 1마디", style = OstType.BodySmall.copy(color = Ost.TextSub))
            }
        }
    }
}

@Composable
private fun About(onSynthTest: (() -> Unit)?, synthStatus: String?) {
    Text("앱 정보", style = OstType.Display)
    Spacer(Modifier.height(25.dp))
    Text("오늘의 OST ${BuildConfig.VERSION_NAME}", style = OstType.Lead)
    if (BuildConfig.DEBUG) {
        Spacer(Modifier.height(40.dp))
        Column(Modifier.fillMaxWidth().card().padding(34.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("개발용 · 소리 엔진 테스트", style = OstType.Section.copy(color = Ost.Yellow))
            Text("두 사운드폰트로 피아노·베이스·드럼·스트링·브라스·코러스를 렌더해 재생합니다.", style = OstType.Label)
            PrimaryButton(if (onSynthTest == null) "렌더링 중..." else "소리 엔진 테스트", { onSynthTest?.invoke() }, height = 66.dp)
            synthStatus?.let { Text(it, style = OstType.Label.copy(lineHeight = 28.sp)) }
        }
    }
}

@Composable
private fun Placeholder(title: String) {
    Text(title, style = OstType.Display)
    Spacer(Modifier.height(25.dp))
    Text("준비 중인 메뉴예요.", style = OstType.Lead)
}
