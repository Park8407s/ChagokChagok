package com.kumhoht.chagokchagok.ui.model

import com.kumhoht.chagokchagok.core.model.Genre
import com.kumhoht.chagokchagok.data.BarCodec
import com.kumhoht.chagokchagok.data.TrackEntity
import com.kumhoht.chagokchagok.engine.session.BarEvent

/** 저장된 트랙(Room) → 화면 데이터 */
object AlbumMapper {

    fun genreOf(t: TrackEntity) = Genre.entries.firstOrNull { it.name == t.genre } ?: Genre.US_POP

    fun trackUi(t: TrackEntity): TrackUi = TrackUiFactory.from(
        id = t.id, number = t.numberInDay, title = t.title, genre = genreOf(t), bars = BarCodec.decode(t.bars),
        seconds = t.seconds, distanceKm = t.distanceKm, driveMinutes = t.driveMinutes,
    )

    /** "11월 6일 아침 드라이브" → "아침 드라이브" (앨범 목록에는 날짜를 빼고 쓴다) */
    private fun shortTitle(t: TrackEntity) = t.title.substringAfter("일 ", t.title)

    private fun dateTitle(dayKey: String): String {
        val (_, m, d) = dayKey.split('-').map { it.toInt() }
        return "${m}월 ${d}일"
    }

    private fun rough(t: TrackEntity) = BarCodec.decode(t.bars).count { it.ev == BarEvent.HARD_ACCEL || it.ev == BarEvent.HARD_BRAKE || it.over }

    /** 날짜별 앨범 (최근 날짜 먼저, 그날 트랙은 순서대로) */
    fun days(tracks: List<TrackEntity>): List<DayAlbumUi> =
        tracks.groupBy { it.dayKey }.toSortedMap(compareByDescending { it }).map { (day, list) ->
            val ordered = list.sortedBy { it.numberInDay }
            DayAlbumUi(
                dateTitle = dateTitle(day),
                genre = ordered.groupingBy { genreOf(it) }.eachCount().maxBy { it.value }.key,
                trackTitles = ordered.map(::shortTitle),
                coverSeed = ordered.first().id * 7919,
                roughEvents = ordered.sumOf(::rough),
                trackIds = ordered.map { it.id },
            )
        }

    fun month(tracks: List<TrackEntity>, month: Int): MonthSummaryUi {
        val longest = tracks.maxBy { it.seconds }
        return MonthSummaryUi(
            month = month, trackCount = tracks.size,
            topGenre = tracks.groupingBy { genreOf(it) }.eachCount().maxBy { it.value }.key,
            cleanTracks = tracks.count { t -> BarCodec.decode(t.bars).none { it.ev == BarEvent.HARD_BRAKE } },
            longestTitle = shortTitle(longest), longestSeconds = longest.seconds.toInt(),
        )
    }
}
