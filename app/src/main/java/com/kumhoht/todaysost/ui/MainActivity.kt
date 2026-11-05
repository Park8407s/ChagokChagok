package com.kumhoht.todaysost.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kumhoht.todaysost.R
import com.kumhoht.todaysost.ui.theme.Ost
import com.kumhoht.todaysost.ui.theme.OstTheme

/**
 * M0: 빈 홈 화면. 앱 이름과 한 줄 소개만 보여준다.
 * 도착 화면·앨범·가상 주행 샘플은 M3 이후에 붙인다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OstTheme { HomePlaceholder() } }
    }
}

@Composable
fun HomePlaceholder() {
    Column(
        Modifier.fillMaxSize().background(Ost.Night),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("♫", style = MaterialTheme.typography.displaySmall, color = Ost.Spot)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge, color = Ost.TextSoft)
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomePlaceholderPreview() {
    OstTheme { HomePlaceholder() }
}
