package com.kumhoht.todaysost.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kumhoht.todaysost.engine.session.Bar
import com.kumhoht.todaysost.engine.session.BarEvent
import kotlinx.coroutines.flow.Flow

/** 트랙 1개 = 운전 1번. 하루 앨범·월간 OST 는 이 표를 날짜로 묶어 만든다. */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    /** "2026-11-06" */
    val dayKey: String,
    /** 그날 몇 번째 트랙 (1부터) */
    val numberInDay: Int,
    val title: String,
    /** Genre.name */
    val genre: String,
    /** BarMode.name */
    val barMode: String,
    /** 마디 목록 ([BarCodec]) */
    val bars: String,
    val seconds: Double,
    val distanceKm: Double,
    val driveMinutes: Int,
    val filePath: String,
    val seen: Boolean = false,
)

@Dao
interface TrackDao {
    @Insert suspend fun insert(t: TrackEntity): Long
    @Query("SELECT * FROM tracks ORDER BY createdAt DESC") fun all(): Flow<List<TrackEntity>>
    @Query("SELECT COUNT(*) FROM tracks WHERE dayKey = :day") suspend fun countForDay(day: String): Int
    @Query("SELECT * FROM tracks WHERE id = :id") suspend fun get(id: Long): TrackEntity?
    @Query("SELECT * FROM tracks WHERE seen = 0 ORDER BY createdAt DESC LIMIT 1") fun latestUnseen(): Flow<TrackEntity?>
    @Query("UPDATE tracks SET seen = 1 WHERE id = :id") suspend fun markSeen(id: Long)
    @Query("DELETE FROM tracks") suspend fun deleteAll()
}

@Database(entities = [TrackEntity::class], version = 1, exportSchema = false)
abstract class OstDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao

    companion object {
        fun create(context: Context): OstDatabase =
            Room.databaseBuilder(context, OstDatabase::class.java, "ost.db").build()
    }
}

/** 마디 목록 ↔ 문자열 "speed,ev,sig,over;..." */
object BarCodec {
    fun encode(bars: List<Bar>): String = bars.joinToString(";") { b ->
        "${b.speed},${b.ev?.name ?: ""},${b.sig ?: ""},${if (b.over) 1 else 0}"
    }

    fun decode(s: String): List<Bar> = if (s.isEmpty()) emptyList() else s.split(';').map { part ->
        val f = part.split(',')
        Bar(
            speed = f[0].toInt(),
            ev = f[1].takeIf { it.isNotEmpty() }?.let { BarEvent.valueOf(it) },
            sig = f[2].firstOrNull(),
            over = f[3] == "1",
        )
    }
}
