package com.kumhoht.todaysost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumhoht.todaysost.R
import com.kumhoht.todaysost.ui.components.AlbumCover
import com.kumhoht.todaysost.ui.components.OstIcon
import com.kumhoht.todaysost.ui.components.OutlineButton
import com.kumhoht.todaysost.ui.components.Pill
import com.kumhoht.todaysost.ui.components.PrimaryButton
import com.kumhoht.todaysost.ui.components.TimelineChart
import com.kumhoht.todaysost.ui.components.card
import com.kumhoht.todaysost.ui.components.dashedBorder
import com.kumhoht.todaysost.ui.components.iconRes
import com.kumhoht.todaysost.ui.model.EventKind
import com.kumhoht.todaysost.ui.model.TrackUi
import com.kumhoht.todaysost.ui.model.formatDuration
import com.kumhoht.todaysost.ui.theme.Ost
import com.kumhoht.todaysost.ui.theme.OstType
import com.kumhoht.todaysost.ui.theme.color
import com.kumhoht.todaysost.ui.theme.shortName

/** 그림 3·4. 도착 화면 (+ 그림 8-1 심사용 데모 진입 버튼) */
@Composable
fun ArrivalScreen(
    track: TrackUi,
    progress: Float,
    showDemoEntry: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onOpenDemo: () -> Unit,
) {
    val accent = track.genre.color
    Row(Modifier.fillMaxSize().padding(start = 64.dp, end = 49.dp, top = 20.dp)) {
        // 왼쪽: 커버와 버튼
        Column(Modifier.width(483.dp)) {
            AlbumCover(track.id, accent, track.roughEvents, Modifier.size(483.dp))
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                PrimaryButton(if (playing) "멈추기" else "이 트랙 듣기", onPlay, Modifier.width(264.dp), icon = if (playing) R.drawable.ic_stop else R.drawable.ic_play)
                OutlineButton(onShare, Modifier.width(98.dp), contentPadding = PaddingValues(0.dp)) {
                    OstIcon(R.drawable.ic_share, Ost.Text, 30.dp)
                }
            }
            if (showDemoEntry) {
                Spacer(Modifier.height(26.dp))
                Column(
                    Modifier.fillMaxWidth().height(158.dp).dashedBorder(Ost.Yellow, 20.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenDemo).padding(horizontal = 26.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("심사용 데모 · 출품 빌드 전용", style = OstType.Caption.copy(color = Ost.Yellow, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OstIcon(R.drawable.ic_play, Ost.Yellow, 24.dp)
                        Spacer(Modifier.size(14.dp))
                        Text("가상 주행 샘플 들어보기", style = OstType.Button)
                    }
                    Text("장르별 완성 트랙 10곡 · 지금 만들기", style = OstType.Caption)
                }
            }
        }
        Spacer(Modifier.width(56.dp))

        // 오른쪽: 곡 정보와 해설
        Column(Modifier.weight(1f)) {
            Text("도착했어요. 오늘의 OST ${ordinal(track.number)} 트랙이 완성됐어요.", style = OstType.BodySmall.copy(color = Ost.TextSub))
            Spacer(Modifier.height(10.dp))
            Text(
                buildAnnotatedString {
                    withStyle(OstType.Display.copy(color = Ost.Yellow).toSpanStyle()) { append(track.numberText) }
                    append(" ")
                    append(track.title)
                },
                style = OstType.Display,
            )
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.height(46.dp).clip(Pill).background(accent).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(track.genre.shortName, style = OstType.Label.copy(color = Ost.Bg, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                }
                Spacer(Modifier.size(20.dp))
                Text(
                    "%.1fkm · %d분 운전 · %d마디 → %s".format(track.distanceKm, track.driveMinutes, track.bars, formatDuration(track.seconds)),
                    style = OstType.BodySmall.copy(color = Ost.TextSub),
                )
            }
            Spacer(Modifier.height(38.dp))
            // 재생 진행바
            Box(Modifier.fillMaxWidth().height(12.dp).clip(Pill).background(Ost.CardHigh)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0.002f, 1f)).height(12.dp).clip(Pill).background(accent))
            }
            Spacer(Modifier.height(39.dp))
            Column(Modifier.fillMaxWidth().height(424.dp).card().padding(horizontal = 32.dp, vertical = 30.dp)) {
                Text("이 곡은 이렇게 만들어졌어요", style = OstType.Section)
                Spacer(Modifier.height(17.dp))
                TimelineChart(
                    bars = track.timeline, color = accent, labels = track.labels,
                    modifier = Modifier.padding(end = 0.dp), chartHeight = 288.dp,
                )
            }
            Spacer(Modifier.height(45.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(25.dp)) {
                    track.notes.forEach { note ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OstIcon(note.icon.iconRes, noteTint(note.icon, accent), 36.dp)
                            Spacer(Modifier.size(20.dp))
                            Text(note.text, style = OstType.Body)
                        }
                    }
                }
                Column(Modifier.width(345.dp).height(214.dp).card().padding(30.dp)) {
                    Text("마지막 화음", style = OstType.Label)
                    Spacer(Modifier.height(10.dp))
                    Text(track.endingTitle, style = OstType.Title)
                    Spacer(Modifier.height(14.dp))
                    Text(track.endingBody, style = OstType.Label.copy(lineHeight = 32.sp))
                }
            }
        }
    }
}

private fun noteTint(kind: EventKind, accent: androidx.compose.ui.graphics.Color) = when (kind) {
    EventKind.HIGHWAY, EventKind.SPEEDING -> accent
    EventKind.HARD_ACCEL, EventKind.HARD_BRAKE -> Ost.Yellow
    else -> Ost.Text
}

private fun ordinal(n: Int) = when (n) {
    1 -> "첫 번째"; 2 -> "두 번째"; 3 -> "세 번째"; 4 -> "네 번째"; 5 -> "다섯 번째"
    else -> "${n}번째"
}
