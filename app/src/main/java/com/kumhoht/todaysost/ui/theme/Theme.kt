package com.kumhoht.todaysost.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 임시 색상 (밤하늘 + 조명 톤). 화면 시안 확정 후 교체한다. */
object Ost {
    val Night = Color(0xFF141226)      // 앱 배경
    val Panel = Color(0xFF1F1C38)      // 카드
    val Spot = Color(0xFFFFC857)       // 강조 (음표, 재생 버튼)
    val Text = Color(0xFFF4F1FF)
    val TextSoft = Color(0xFFA9A3C9)
}

private val scheme = darkColorScheme(
    primary = Ost.Spot,
    onPrimary = Ost.Night,
    background = Ost.Night,
    surface = Ost.Panel,
    onBackground = Ost.Text,
    onSurface = Ost.Text,
)

/** 차량 화면에서 읽기 쉽게 기본 글자를 크게 (붕붕멍과 같은 크기 체계) */
private val typography = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Ost.Text),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ost.Text),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ost.Text),
    titleMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ost.Text),
    bodyLarge = TextStyle(fontSize = 20.sp, color = Ost.Text),
    bodyMedium = TextStyle(fontSize = 18.sp, color = Ost.Text),
    labelMedium = TextStyle(fontSize = 16.sp, color = Ost.TextSoft),
)

@Composable
fun OstTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
