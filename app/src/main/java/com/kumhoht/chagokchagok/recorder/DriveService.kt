package com.kumhoht.chagokchagok.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kumhoht.chagokchagok.OstApp
import com.kumhoht.chagokchagok.ShortDrive
import com.kumhoht.chagokchagok.R
import com.kumhoht.chagokchagok.core.model.TrackRules
import com.kumhoht.chagokchagok.data.BarCodec
import com.kumhoht.chagokchagok.data.TrackEntity
import com.kumhoht.chagokchagok.engine.session.DriveRecorder
import com.kumhoht.chagokchagok.engine.session.DriveSession
import com.kumhoht.chagokchagok.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 주행 기록 Foreground Service.
 * - 1초마다 차량 값을 기록 (주행 중에는 화면도 소리도 없다)
 * - 도착(기어 P + 0km/h): 최소 운행 시간(TrackRules.MIN_DRIVE_SECONDS) 미만이면 버리고, 아니면 마디 → 작곡 → 렌더 → 저장 → 재생
 * 알림은 소리·진동 없는 상태 알림만 쓴다.
 */
class DriveService : Service() {
    private val app by lazy { OstApp.of(this) }
    private val recorder = DriveRecorder()
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) }
        )
        ServiceCompat.startForeground(this, NOTIF_ID, notification(false), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        loop = app.scope.launch {
            var lastRecording = false
            while (isActive) {
                val v = app.telemetry.current()
                val trip = recorder.tick(System.currentTimeMillis(), v.speedKmh.toDouble(), v.gear, v.brake, v.turn, v.speedLimit)
                if (recorder.recording != lastRecording) {
                    lastRecording = recorder.recording
                    Log.i(TAG, if (lastRecording) "운행 시작 (기어 ${v.gear})" else "운행 끝")
                    getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(lastRecording))
                    if (lastRecording) app.player.stop() // 주행 중에는 소리 없이 (1차 범위)
                }
                if (recorder.recording && recorder.recordedSeconds % 30 == 0) Log.i(TAG, "기록 중 ${recorder.recordedSeconds}초, 속도 ${v.speedKmh}, 규제 ${v.restricted}")
                app.setRecording(app.recording.value.copy(recording = recorder.recording, seconds = recorder.recordedSeconds))
                if (trip != null) onArrived(trip)
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        loop?.cancel()
        super.onDestroy()
    }

    private suspend fun onArrived(trip: DriveRecorder.Trip) {
        if (trip.tooShort) {
            Log.i(TAG, "${trip.seconds}초 운행: 최소 ${TrackRules.MIN_DRIVE_SECONDS}초 미만이라 트랙을 만들지 않음")
            // 화면에 "N초 운전했어요. 30초 이상 운전하면…" 안내 (홈이 보이도록 앱을 앞으로)
            app.setRecording(app.recording.value.copy(shortDrive = ShortDrive(trip.seconds, trip.endedAt)))
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val genre = app.genre
        val mode = app.barMode
        app.setRecording(app.recording.value.copy(composing = true))
        try {
            val bars = DriveSession.segment(trip.log, mode).bars
            val made = app.maker.make(genre, bars, "track_${trip.endedAt}")
            val day = dayKey(trip.endedAt)
            val number = app.db.tracks().countForDay(day) + 1
            val id = app.db.tracks().insert(
                TrackEntity(
                    createdAt = trip.endedAt, dayKey = day, numberInDay = number, title = title(trip.startedAt),
                    genre = genre.name, barMode = mode.name, bars = BarCodec.encode(bars), seconds = made.seconds,
                    distanceKm = trip.distanceKm, driveMinutes = trip.minutes, filePath = made.file.absolutePath,
                )
            )
            Log.i(TAG, "트랙 #$id 저장: ${trip.seconds}초 운행 → ${bars.size}마디 → %.1f초 ($genre, $mode)".format(made.seconds))
            // 도착하면 들려준다 (주차 상태라 소리 가능)
            app.player.play(
                File(made.file.absolutePath),
                title = "Track %02d %s".format(number, title(trip.startedAt)),
                subtitle = "오늘의 OST · ${genre.label} · %d분 %02d초".format(made.seconds.toInt() / 60, made.seconds.toInt() % 60),
            )
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_TRACK, id))
        } catch (t: Throwable) {
            Log.e(TAG, "곡 만들기 실패", t)
        } finally {
            app.setRecording(app.recording.value.copy(composing = false))
        }
    }

    private fun notification(recording: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_genre_pop)
            .setContentTitle(if (recording) "차곡차곡이 운전을 기록하고 있어요" else "차곡차곡")
            .setContentText(if (recording) "도착하면 이번 운전으로 만든 곡을 들려드릴게요" else "기어를 D로 바꾸면 기록이 시작돼요")
            .setOngoing(true).setSilent(true).setContentIntent(open)
            .build()
    }

    companion object {
        private const val TAG = "DriveService"
        private const val CHANNEL = "drive"
        private const val NOTIF_ID = 1

        fun start(c: Context) = runCatching { c.startForegroundService(Intent(c, DriveService::class.java)) }
            .onFailure { Log.w(TAG, "서비스 시작 실패: ${it.message}") }

        /**
         * 앱 끄기: 기록 서비스를 멈추고 차량 신호 수신·재생을 끊은 뒤 앱을 완전히 닫는다.
         * 앱을 다시 열면 다시 켜진다. 시동(부팅) 때 저절로 켜지지 않는다.
         */
        fun shutdown(activity: android.app.Activity) {
            Log.i(TAG, "앱 끄기")
            val app = OstApp.of(activity)
            app.player.stop()
            activity.stopService(Intent(activity, DriveService::class.java))
            app.telemetry.stop()
            activity.finishAndRemoveTask()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 400)
        }

        fun dayKey(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(ms)

        /** "11월 6일 아침 드라이브" (경로 이름을 알 수 없어 시간대로 짓는다) */
        fun title(ms: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = ms }
            val part = when (c.get(Calendar.HOUR_OF_DAY)) { in 5..9 -> "아침"; in 10..15 -> "낮"; in 16..19 -> "저녁"; else -> "밤" }
            return "${c.get(Calendar.MONTH) + 1}월 ${c.get(Calendar.DAY_OF_MONTH)}일 $part 드라이브"
        }
    }
}

