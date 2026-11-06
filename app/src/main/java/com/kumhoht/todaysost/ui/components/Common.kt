package com.kumhoht.todaysost.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kumhoht.todaysost.R
import com.kumhoht.todaysost.core.model.Genre
import com.kumhoht.todaysost.ui.model.EventKind
import com.kumhoht.todaysost.ui.theme.Ost
import com.kumhoht.todaysost.ui.theme.OstType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Pill = RoundedCornerShape(percent = 50)
val CardShape = RoundedCornerShape(26.dp)

@get:DrawableRes
val Genre.iconRes: Int
    get() = when (this) {
        Genre.US_POP -> R.drawable.ic_genre_pop
        Genre.POP_BALLAD -> R.drawable.ic_genre_ballad
        Genre.CHILDREN -> R.drawable.ic_genre_children
        Genre.TROT -> R.drawable.ic_genre_trot
        Genre.DANCE -> R.drawable.ic_genre_dance
    }

@get:DrawableRes
val EventKind.iconRes: Int
    get() = when (this) {
        EventKind.TURN_RIGHT -> R.drawable.ic_turn_right
        EventKind.TURN_LEFT -> R.drawable.ic_turn_left
        EventKind.HARD_ACCEL -> R.drawable.ic_accel
        EventKind.HARD_BRAKE -> R.drawable.ic_brake
        EventKind.SPEEDING -> R.drawable.ic_speeding
        EventKind.HIGHWAY -> R.drawable.ic_highway
        EventKind.STOP -> R.drawable.ic_stop
        EventKind.PARKED -> R.drawable.ic_parking
    }

@Composable
fun OstIcon(@DrawableRes res: Int, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** 카드 바탕 (시안: #222E3D, 모서리 26) */
fun Modifier.card(shape: Shape = CardShape, color: Color = Ost.Card, border: Color? = null): Modifier =
    clip(shape).background(color).let { if (border != null) it.border(BorderStroke(2.dp, border), shape) else it }

/** 점선 테두리 (심사용 데모 표시) */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 2.dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
    )
}

/** 노란 주 버튼 (시안: 높이 81, 알약 모양) */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = R.drawable.ic_play,
    height: Dp = 81.dp,
    color: Color = Ost.Yellow,
    style: TextStyle = OstType.Button,
    contentPadding: PaddingValues = PaddingValues(horizontal = 39.dp),
) {
    Row(
        modifier.height(height).clip(Pill).background(color).clickable(onClick = onClick).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) OstIcon(icon, Ost.Bg, 28.dp)
        Text(text, style = style.copy(color = Ost.Bg))
    }
}

/** 외곽선 버튼 (시안: 테두리 #34445A) */
@Composable
fun OutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 81.dp,
    fill: Color = Color.Transparent,
    border: Color = Ost.Line,
    contentPadding: PaddingValues = PaddingValues(horizontal = 34.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.height(height).clip(Pill).background(fill).border(BorderStroke(2.dp, border), Pill)
            .clickable(onClick = onClick).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        content = content,
    )
}

/** 앱 안 화면 */
enum class Screen(val label: String) { HOME("홈"), ARRIVAL("도착"), ALBUM("앨범"), DEMO("데모"), SETTINGS("설정") }

/**
 * 맨 위 줄: [‹ 이전] 시각 · 앱 이름 · 화면 이동 버튼.
 * 시안에는 화면 이동 방법이 없고 Pleos 에는 뒤로 가기 버튼이 없어서 추가했다.
 * 차 안에서 누르기 쉽게 버튼 높이 52dp, 글자 22sp.
 *
 * @param showArrival 방금 만든 곡(도착 화면)이 있으면 '방금 곡' 버튼을 보여 준다
 */
@Composable
fun OstStatusBar(
    current: Screen,
    canGoBack: Boolean,
    showArrival: Boolean,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }
    val navStyle = OstType.BodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    Box(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 40.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (canGoBack) {
                Text(
                    "‹ 이전", style = navStyle,
                    modifier = Modifier.height(52.dp).clip(Pill).border(BorderStroke(2.dp, Ost.Line), Pill)
                        .clickable(onClick = onBack).padding(horizontal = 24.dp, vertical = 11.dp),
                )
            }
            Text(SimpleDateFormat("HH:mm", Locale.KOREA).format(now), style = OstType.Status)
        }
        Text("오늘의 OST", style = OstType.Status, modifier = Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val items = buildList {
                add(Screen.HOME)
                if (showArrival) add(Screen.ARRIVAL)
                add(Screen.ALBUM); add(Screen.DEMO); add(Screen.SETTINGS)
            }
            items.forEach { s ->
                val on = s == current
                Text(
                    if (s == Screen.ARRIVAL) "방금 곡" else s.label,
                    style = navStyle.copy(color = if (on) Ost.Bg else Ost.Text),
                    modifier = Modifier.height(52.dp).clip(Pill)
                        .background(if (on) Ost.Yellow else Color.Transparent)
                        .border(BorderStroke(2.dp, if (on) Ost.Yellow else Ost.Line), Pill)
                        .clickable { onNavigate(s) }.padding(horizontal = 24.dp, vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
fun HSpace(w: Dp) = Box(Modifier.width(w))
