package com.kumhoht.todaysost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kumhoht.todaysost.ui.components.AlbumCover
import com.kumhoht.todaysost.ui.components.OutlineButton
import com.kumhoht.todaysost.ui.components.Pill
import com.kumhoht.todaysost.ui.components.PrimaryButton
import com.kumhoht.todaysost.ui.components.card
import com.kumhoht.todaysost.ui.model.DayAlbumUi
import com.kumhoht.todaysost.ui.model.MonthSummaryUi
import com.kumhoht.todaysost.ui.model.formatDuration
import com.kumhoht.todaysost.ui.theme.Ost
import com.kumhoht.todaysost.ui.theme.OstType
import com.kumhoht.todaysost.ui.theme.color
import com.kumhoht.todaysost.ui.theme.shortName

/** 그림 5. 내 OST 앨범 화면 ('차곡차곡') */
@Composable
fun AlbumScreen(days: List<DayAlbumUi>, month: MonthSummaryUi, onPlayMonth: () -> Unit) {
    var byTrack by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 20.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("내 OST 앨범", style = OstType.Display)
                Spacer(Modifier.height(20.dp))
                Text("운전 1번 = 1트랙 · 하루의 트랙이 모여 그날의 OST가 돼요 · 이번 달 ${month.trackCount}트랙", style = OstType.Lead)
            }
            Row(Modifier.padding(top = 41.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tab("날짜별", !byTrack) { byTrack = false }
                Tab("트랙별", byTrack) { byTrack = true }
                PrimaryButton("${month.month}월의 OST 듣기", onPlayMonth, icon = null)
            }
        }
        Spacer(Modifier.height(37.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (byTrack) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(days.flatMap { d -> d.trackTitles.mapIndexed { i, t -> Triple(d, i + 1, t) } }) { (d, n, t) ->
                        Row(Modifier.fillMaxWidth().card().padding(horizontal = 30.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d".format(n), style = OstType.BodySmall.copy(color = Ost.Yellow))
                            Spacer(Modifier.size(16.dp))
                            Text(t, style = OstType.BodySmall, modifier = Modifier.weight(1f))
                            Text("${d.dateTitle} · ${d.genre.shortName}", style = OstType.Label)
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    days.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(403.dp)) {
                            row.forEach { DayItem(it, Modifier.width(391.dp)) }
                        }
                    }
                }
            }
        }

        // 월간 결산
        Row(
            Modifier.fillMaxWidth().height(130.dp).card().padding(horizontal = 37.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Summary("가장 많이 고른 장르", month.topGenre.shortName, Modifier.width(247.dp))
            Summary("끊김 없이 끝난 트랙", "${month.cleanTracks} / ${month.trackCount}", Modifier.width(248.dp))
            Summary("가장 긴 트랙", "${month.longestTitle} · ${formatDuration(month.longestSeconds)}", Modifier)
        }
        Spacer(Modifier.height(35.dp))
    }
}

@Composable
private fun Tab(text: String, selected: Boolean, onClick: () -> Unit) {
    OutlineButton(
        onClick, Modifier.width(138.dp),
        fill = if (selected) Ost.CardHigh else androidx.compose.ui.graphics.Color.Transparent,
        contentPadding = PaddingValues(0.dp),
    ) { Text(text, style = OstType.Body) }
}

@Composable
private fun DayItem(day: DayAlbumUi, modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.size(230.dp)) {
            AlbumCover(day.coverSeed, day.genre.color, day.roughEvents, Modifier.size(230.dp), corner = 12.dp)
            Box(
                Modifier.offset(x = 132.dp, y = 16.dp).height(39.dp).width(82.dp).clip(Pill).background(Ost.CoverBg),
                contentAlignment = Alignment.Center,
            ) { Text("${day.trackTitles.size}트랙", style = OstType.Label.copy(color = Ost.Text, fontWeight = FontWeight.Bold)) }
        }
        Spacer(Modifier.height(12.dp))
        Text("${day.dateTitle}의 OST", style = OstType.Section.copy(fontSize = OstType.Body.fontSize))
        Spacer(Modifier.height(8.dp))
        day.trackTitles.forEachIndexed { i, t ->
            Row(Modifier.height(33.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(i + 1), style = OstType.Label.copy(color = Ost.Yellow))
                Spacer(Modifier.size(6.dp))
                Text(t, style = OstType.Label)
            }
        }
    }
}

@Composable
private fun Summary(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = OstType.Label)
        Text(value, style = OstType.Heading)
    }
}
