package com.kumhoht.chagokchagok.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kumhoht.chagokchagok.core.model.Genre

/**
 * Figma 시안(reference/figma)에서 뽑은 색.
 * 시안 크기 1682×1210 은 Pleos 앱 창(160dpi)과 같아서 시안 1px = 1dp = 1sp 로 옮긴다.
 */
object Ost {
    val Bg = Color(0xFF18212C)          // 화면 배경
    val Card = Color(0xFF222E3D)        // 카드
    val CardHigh = Color(0xFF2B394B)    // 선택된 탭·메뉴, 진행바 바탕
    val Line = Color(0xFF34445A)        // 카드·외곽선 버튼 테두리
    val CoverBg = Color(0xFF121A24)     // 앨범 커버 바탕
    val Text = Color(0xFFEEF1F5)
    val TextSub = Color(0xFF8C9AAD)
    val Idle = Color(0xFF475465)        // 타임라인의 출발·정차 막대
    val Yellow = Color(0xFFF2C14E)      // 강조 (주 버튼, 트랙 번호, 과속·급제동 표시)

    val Orange = Color(0xFFFF7A45)      // 팝
    val Purple = Color(0xFFB7A6F5)      // 발라드
    val Green = Color(0xFF7ED6A5)       // 동요
    val Pink = Color(0xFFFF5FA2)        // 트로트
    val Cyan = Color(0xFF4CC9F0)        // 댄스
}

/** 장르별 표시 정보 (시안 기준) */
val Genre.color: Color
    get() = when (this) {
        Genre.POP -> Ost.Orange
        Genre.BALLAD -> Ost.Purple
        Genre.CHILDREN -> Ost.Green
        Genre.TROT -> Ost.Pink
        Genre.DANCE -> Ost.Cyan
    }

/** 화면에 쓰는 짧은 이름 (팝·발라드·동요·트로트·댄스) */
val Genre.shortName: String
    get() = when (this) {
        Genre.POP -> "팝"
        Genre.BALLAD -> "발라드"
        Genre.CHILDREN -> "동요"
        Genre.TROT -> "트로트"
        Genre.DANCE -> "댄스"
    }

val Genre.tagline: String
    get() = when (this) {
        Genre.POP -> "밝고 리듬감 있게"
        Genre.BALLAD -> "잔잔하고 서정적으로"
        Genre.CHILDREN -> "아이와 함께"
        Genre.TROT -> "흥겹게 쿵짝"
        Genre.DANCE -> "신나게 쿵쿵"
    }

/** 글자 스타일. 크기는 시안 글자 상자 높이에서 역산했다. 글꼴은 시스템 기본(Noto Sans CJK). */
object OstType {
    private fun s(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Ost.Text, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontSize = size.sp, fontWeight = weight, color = color, lineHeight = lineHeight)

    val Status = s(20, color = Ost.TextSub)
    val Display = s(46, FontWeight.Bold)                     // 화면 제목
    val Lead = s(24, color = Ost.TextSub)                    // 제목 아래 설명
    val CardTitle = s(32, FontWeight.Bold)                   // 장르 카드 이름
    val Title = s(30, FontWeight.Bold)                       // 마지막 화음, 라이선스 이름
    val Heading = s(28, FontWeight.Bold)                     // 트랙 이름(작게), 결산 값
    val Section = s(22, FontWeight.Bold)                     // 카드 안 소제목
    val Button = s(24, FontWeight.Bold)
    val Body = s(24)                                         // 곡 해설 문장
    val BodySmall = s(22)
    val Label = s(20, color = Ost.TextSub)
    val Caption = s(18, color = Ost.TextSub)
    val Tiny = s(17, color = Ost.TextSub)
}

private val scheme = darkColorScheme(
    primary = Ost.Yellow,
    onPrimary = Ost.Bg,
    background = Ost.Bg,
    surface = Ost.Card,
    onBackground = Ost.Text,
    onSurface = Ost.Text,
)

@Composable
fun OstTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
