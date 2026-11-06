package com.kumhoht.todaysost.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kumhoht.todaysost.R
import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.ui.components.AlbumCover
import com.kumhoht.todaysost.ui.components.CardShape
import com.kumhoht.todaysost.ui.components.OstIcon
import com.kumhoht.todaysost.ui.components.OutlineButton
import com.kumhoht.todaysost.ui.components.card
import com.kumhoht.todaysost.ui.components.iconRes
import com.kumhoht.todaysost.ui.model.TrackUi
import com.kumhoht.todaysost.ui.model.formatDuration
import com.kumhoht.todaysost.ui.theme.Ost
import com.kumhoht.todaysost.ui.theme.OstType
import com.kumhoht.todaysost.ui.theme.color
import com.kumhoht.todaysost.ui.theme.shortName
import com.kumhoht.todaysost.ui.theme.tagline

/** 그림 2. 출발 전 홈 화면 */
@Composable
fun HomeScreen(
    selected: Genre,
    onSelect: (Genre) -> Unit,
    yesterday: TrackUi?,
    yesterdayTrackCount: Int,
    onReplay: () -> Unit,
    onOpenAlbum: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 6.dp)) {
        Text("오늘은 어떤 곡으로 달릴까요?", style = OstType.Display)
        Spacer(Modifier.height(22.dp))
        Text("고른 장르로 이번 운전이 오늘의 OST 한 트랙이 됩니다. 도착하면 들려드릴게요.", style = OstType.Lead)
        Spacer(Modifier.height(60.dp))

        Row(Modifier.fillMaxWidth().height(506.dp), horizontalArrangement = Arrangement.spacedBy(25.dp)) {
            Genre.entries.forEach { g -> GenreCard(g, g == selected, { onSelect(g) }, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(60.dp))

        Row(Modifier.fillMaxWidth().height(237.dp), horizontalArrangement = Arrangement.spacedBy(33.dp)) {
            Row(
                Modifier.weight(923f).fillMaxHeight().card().clickable(onClick = onOpenAlbum).padding(horizontal = 37.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (yesterday != null) {
                    AlbumCover(yesterday.id, yesterday.genre.color, yesterday.roughEvents, Modifier.size(172.dp), corner = 10.dp)
                    Spacer(Modifier.size(30.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("어제의 OST · ${yesterdayTrackCount}트랙", style = OstType.Label)
                        Text(
                            buildAnnotatedString {
                                withStyle(OstType.Heading.copy(color = Ost.Yellow).toSpanStyle()) { append(yesterday.numberText) }
                                append(" ")
                                append(yesterday.title)
                            },
                            style = OstType.Heading,
                        )
                        Text("${yesterday.genre.shortName} · ${formatDuration(yesterday.seconds)}", style = OstType.Label)
                    }
                    OutlineButton(onReplay, Modifier.padding(end = 0.dp), height = 77.dp) {
                        OstIcon(R.drawable.ic_play, Ost.Text, 28.dp)
                        Text("다시 듣기", style = OstType.Body)
                    }
                } else {
                    Text("아직 만든 트랙이 없어요. 첫 운전을 시작해 보세요.", style = OstType.Body.copy(color = Ost.TextSub))
                }
            }
            Row(
                Modifier.weight(598f).fillMaxHeight().card().padding(horizontal = 39.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OstIcon(R.drawable.ic_parking, Ost.Yellow, 64.dp)
                Spacer(Modifier.size(26.dp))
                Column {
                    Text("기어를 D로 바꾸면 기록이 시작돼요.", style = OstType.Body)
                    Text("운전 중에는 화면과 소리가 없어요.", style = OstType.Body.copy(color = Ost.TextSub))
                }
            }
        }
    }
}

@Composable
private fun GenreCard(genre: Genre, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val bg = if (selected) genre.color else Ost.Card
    val fg = if (selected) Ost.Bg else Ost.Text
    Column(
        modifier.fillMaxHeight()
            .card(color = bg)
            .then(if (selected) Modifier else Modifier.border(2.dp, Ost.Line, CardShape))
            .clickable(onClick = onClick)
            .padding(start = 33.dp, top = 33.dp, end = 20.dp, bottom = 39.dp),
    ) {
        OstIcon(genre.iconRes, if (selected) Ost.Bg else genre.color, 74.dp)
        Spacer(Modifier.weight(1f))
        Text(genre.shortName, style = OstType.CardTitle.copy(color = fg))
        Spacer(Modifier.height(15.dp))
        Text(genre.tagline, style = OstType.Label.copy(color = if (selected) Ost.Bg else Ost.TextSub))
    }
}
