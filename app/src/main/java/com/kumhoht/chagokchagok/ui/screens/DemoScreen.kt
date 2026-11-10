package com.kumhoht.chagokchagok.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumhoht.chagokchagok.R
import com.kumhoht.chagokchagok.core.model.Genre
import com.kumhoht.chagokchagok.core.model.TrackRules
import com.kumhoht.chagokchagok.ui.components.OstIcon
import com.kumhoht.chagokchagok.ui.components.PrimaryButton
import com.kumhoht.chagokchagok.ui.components.TimelineChart
import com.kumhoht.chagokchagok.ui.components.card
import com.kumhoht.chagokchagok.ui.components.dashedBorder
import com.kumhoht.chagokchagok.ui.components.iconRes
import com.kumhoht.chagokchagok.ui.model.PlayState
import com.kumhoht.chagokchagok.ui.model.TrackUi
import com.kumhoht.chagokchagok.ui.model.formatDuration
import com.kumhoht.chagokchagok.ui.theme.Ost
import com.kumhoht.chagokchagok.ui.theme.OstType
import com.kumhoht.chagokchagok.ui.theme.color
import com.kumhoht.chagokchagok.ui.theme.shortName
import kotlin.math.roundToInt

/** 가상 주행 시나리오 */
enum class Drive(val title: String) { SMOOTH("A 부드러운 운전"), ROUGH("B 급한 운전") }

/** 그림 8-2. 가상 주행 샘플 들어보기 (심사용 데모, 출품 빌드 전용) */
@Composable
fun DemoScreen(
    smooth: TrackUi,
    rough: TrackUi,
    drive: Drive,
    onDrive: (Drive) -> Unit,
    sample: Pair<Genre, Drive>?,
    onSample: (Genre, Drive) -> Unit,
    onMakeNow: () -> Unit,
    sampleState: (Genre, Drive) -> PlayState = { _, _ -> PlayState.IDLE },
) {
    Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("가상 주행 샘플 들어보기", style = OstType.Display)
            Spacer(Modifier.size(27.dp))
            Text(
                "심사용 데모", style = OstType.Label.copy(color = Ost.Yellow, fontWeight = FontWeight.Bold),
                modifier = Modifier.dashedBorder(Ost.Yellow, 22.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text("실제로 운전하지 않아도, 가상 주행 데이터로 만든 트랙을 바로 들어볼 수 있어요.", style = OstType.Lead)
        Spacer(Modifier.height(43.dp))

        Row(Modifier.fillMaxWidth().height(202.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            DriveCard(Drive.SMOOTH, smooth, "급가속·급제동 0회", drive == Drive.SMOOTH, { onDrive(Drive.SMOOTH) }, Modifier.weight(766f))
            DriveCard(
                Drive.ROUGH, rough, "급가속 ${rough.hardAccel}회 · 급제동 ${rough.hardBrake}회",
                drive == Drive.ROUGH, { onDrive(Drive.ROUGH) }, Modifier.weight(762f),
            )
        }
        Spacer(Modifier.height(34.dp))

        Row(Modifier.fillMaxWidth().height(319.dp), horizontalArrangement = Arrangement.spacedBy(21.dp)) {
            Genre.entries.forEach { g ->
                Column(Modifier.weight(1f).fillMaxHeight().card().padding(horizontal = 21.dp, vertical = 30.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OstIcon(g.iconRes, g.color, 55.dp)
                        Spacer(Modifier.size(14.dp))
                        Text(g.shortName, style = OstType.Heading.copy(fontSize = 26.sp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${g.bpm} BPM · ${smooth.bars}마디", style = OstType.Tiny)
                    Spacer(Modifier.height(20.dp))
                    val sec = formatDuration(TrackRules.songSeconds(smooth.bars, g).roundToInt())
                    SampleButton("A 부드럽게", sec, g.color, sample == (g to Drive.SMOOTH), sampleState(g, Drive.SMOOTH)) { onSample(g, Drive.SMOOTH) }
                    Spacer(Modifier.height(16.dp))
                    SampleButton("B 급하게", sec, g.color, sample == (g to Drive.ROUGH), sampleState(g, Drive.ROUGH)) { onSample(g, Drive.ROUGH) }
                }
            }
        }
        Spacer(Modifier.height(35.dp))

        Row(Modifier.fillMaxWidth().height(133.dp).card().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton("가상 주행으로 지금 만들기", onMakeNow, icon = R.drawable.ic_highway, height = 68.dp)
            Spacer(Modifier.size(28.dp))
            Column {
                Text("저장된 주행 기록을 재생해 앱의 작곡 엔진이 그 자리에서 새 트랙을 만들어요.", style = OstType.BodySmall)
                Text("마디 길이: 기본 모드(주행 40초 = 1마디) · 데모 모드(주행 5초 = 1마디)는 설정에서 전환", style = OstType.BodySmall.copy(color = Ost.TextSub))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("심사·시연을 위한 기능으로, 실제 서비스에서는 제외되거나 첫 실행 안내(샘플 미리 듣기)로 축소될 수 있습니다.", style = OstType.Tiny)
    }
}

@Composable
private fun DriveCard(drive: Drive, track: TrackUi, events: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxHeight().card(border = if (selected) Ost.Yellow else null).clickable(onClick = onClick)
            .padding(start = 30.dp, end = 30.dp, top = 32.dp, bottom = 30.dp),
    ) {
        Column(Modifier.width(220.dp).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(drive.title, style = OstType.Section)
            Spacer(Modifier.height(4.dp))
            Text("같은 출근길 %.1fkm · %d분".format(track.distanceKm, track.driveMinutes), style = OstType.Tiny)
            Text(events, style = OstType.Tiny)
        }
        Spacer(Modifier.size(20.dp))
        Box(Modifier.width(414.dp)) {
            TimelineChart(track.timeline, track.genre.color, chartHeight = 138.dp, barRatio = 0.5f, markerSize = 12.dp)
        }
    }
}

/**
 * 샘플 버튼. 재생 중 = 장르 색으로 채움 + 멈춤 아이콘 + '재생 중', 만드는 중 = '만드는 중…',
 * 멈춘 상태로 마지막에 고른 버튼 = 장르 색 테두리만.
 */
@Composable
private fun SampleButton(label: String, duration: String, accent: Color, selected: Boolean, state: PlayState, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val playing = state == PlayState.PLAYING
    Row(
        Modifier.fillMaxWidth().height(71.dp).clip(shape)
            .background(if (playing) accent else Color.Transparent)
            .border(2.dp, if (playing || selected || state == PlayState.PAUSED) accent else Ost.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (playing) Ost.Bg else Ost.Text
        OstIcon(if (playing) R.drawable.ic_stop else R.drawable.ic_play, fg, 23.dp)
        Spacer(Modifier.size(11.dp))
        Text(label, style = OstType.Label.copy(color = fg, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Text(
            when (state) { PlayState.PLAYING -> "재생 중"; PlayState.PAUSED -> "일시정지"; PlayState.LOADING -> "만드는 중…"; else -> duration },
            style = OstType.Tiny.copy(color = if (playing) Ost.Bg else Ost.TextSub, fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal),
        )
    }
}
