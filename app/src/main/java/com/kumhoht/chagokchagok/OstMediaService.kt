package com.kumhoht.chagokchagok

import android.media.browse.MediaBrowser
import android.os.Bundle
import android.service.media.MediaBrowserService

/**
 * 차량 미디어 연동. Android Automotive 의 미디어 위젯·미디어 센터는 MediaBrowserService 를 가진 앱만
 * 미디어 소스로 인식한다. 재생과 제어는 [com.kumhoht.chagokchagok.ui.OstPlayer] 의 미디어 세션이 맡고,
 * 이 서비스는 그 세션을 차량에 알려 주는 역할만 한다 (곡 목록 탐색은 앱 화면에서 하므로 비워 둠).
 */
class OstMediaService : MediaBrowserService() {
    override fun onCreate() {
        super.onCreate()
        sessionToken = OstApp.of(this).player.session.sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) = BrowserRoot(ROOT, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    private companion object {
        const val ROOT = "chagokchagok_root"
    }
}
