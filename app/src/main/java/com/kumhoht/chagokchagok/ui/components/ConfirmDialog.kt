package com.kumhoht.chagokchagok.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kumhoht.chagokchagok.ui.theme.Ost
import com.kumhoht.chagokchagok.ui.theme.OstType

/** 되돌릴 수 없는 동작(초기화·끄기) 전에 한 번 더 묻는 창 */
@Composable
fun ConfirmDialog(title: String, body: String, confirmText: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onCancel, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(760.dp).card(color = Ost.Bg, border = Ost.Line).padding(44.dp)) {
            Text(title, style = OstType.Title)
            Spacer(Modifier.height(18.dp))
            Text(body, style = OstType.Body.copy(color = Ost.TextSub, lineHeight = 36.sp))
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlineButton(onCancel, Modifier.weight(1f), height = 76.dp, contentPadding = PaddingValues(0.dp)) {
                    Text("취소", style = OstType.Button)
                }
                PrimaryButton(confirmText, onConfirm, Modifier.weight(1f), icon = null, height = 76.dp)
            }
        }
    }
}
