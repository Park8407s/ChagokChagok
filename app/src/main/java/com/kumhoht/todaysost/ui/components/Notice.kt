package com.kumhoht.todaysost.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kumhoht.todaysost.R
import com.kumhoht.todaysost.ui.model.formatDuration
import com.kumhoht.todaysost.ui.theme.Ost
import com.kumhoht.todaysost.ui.theme.OstType

/**
 * 짧은 운전(최소 운행 시간 미만)으로 도착했을 때 안내. 트랙이 안 만들어진 이유를 알려 준다.
 * 화면 아래쪽에 떠 있는 카드 (닫기 또는 잠시 뒤 자동으로 사라짐).
 */
@Composable
fun ShortDriveNotice(seconds: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.width(1100.dp).card(color = Ost.CardHigh, border = Ost.Yellow).padding(horizontal = 36.dp, vertical = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OstIcon(R.drawable.ic_parking, Ost.Yellow, 56.dp)
        Spacer(Modifier.size(26.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${formatDuration(seconds)} 운전했어요. 이번 운전은 곡으로 만들지 않았어요.", style = OstType.Body)
            Text(
                "${formatDuration(com.kumhoht.todaysost.core.model.TrackRules.MIN_DRIVE_SECONDS)} 이상 운전하면 도착할 때 곡을 만들어 들려드려요.",
                style = OstType.Body.copy(color = Ost.TextSub),
            )
        }
        Spacer(Modifier.size(20.dp))
        OutlineButton(onClose, height = 66.dp, contentPadding = PaddingValues(horizontal = 30.dp)) {
            Text("확인", style = OstType.BodySmall)
        }
    }
}
