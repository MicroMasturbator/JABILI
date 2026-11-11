package com.nsfwbili.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** 视频的基本信息（列表里显示用） */
@Entity(tableName = "video")
data class VideoEntity(
    @androidx.room.PrimaryKey val url: String,
    val site: String,
    val title: String,
    val cover: String,
    val duration: String,
    val sub: String,
) {
    fun toItem() = VideoItem(url, title, cover, duration, sub)
    companion object { fun of(v: VideoItem, site: String) = VideoEntity(v.url, site, v.title, v.cover, v.duration, v.sub) }
}

/** 收藏 / 爱心 / 历史：type = fav | like | hist */
@Entity(tableName = "library", primaryKeys = ["type", "url"])
data class LibraryEntry(val type: String, val url: String, val addedAt: Long)

/** 播放进度 */
@Entity(tableName = "progress")
data class ProgressEntity(
    @androidx.room.PrimaryKey val url: String,
    val posMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

data class LibraryRow(
    val type: String, val addedAt: Long,
    val url: String, val title: String, val cover: String, val duration: String, val sub: String,
)

@Dao
interface LibraryDao {
    @Query("SELECT l.type, l.addedAt, v.url, v.title, v.cover, v.duration, v.sub FROM library l JOIN video v ON v.url = l.url ORDER BY l.addedAt DESC")
    fun allEntries(): List<LibraryRow>

    @Query("SELECT * FROM progress")
    fun allProgress(): List<ProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertVideo(v: VideoEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertVideos(v: List<VideoEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertEntry(e: LibraryEntry)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertEntries(e: List<LibraryEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertProgress(p: ProgressEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertProgressAll(p: List<ProgressEntity>)

    @Query("DELETE FROM library WHERE type = :type AND url = :url") fun deleteEntry(type: String, url: String)
    @Query("DELETE FROM library WHERE type = :type") fun clear(type: String)
    @Query("DELETE FROM library WHERE type = 'hist' AND url NOT IN (SELECT url FROM library WHERE type = 'hist' ORDER BY addedAt DESC LIMIT :keep)")
    fun trimHistory(keep: Int)
    @Query("DELETE FROM progress WHERE url NOT IN (SELECT url FROM progress ORDER BY updatedAt DESC LIMIT :keep)")
    fun trimProgress(keep: Int)
}

@Database(entities = [VideoEntity::class, LibraryEntry::class, ProgressEntity::class], version = 1, exportSchema = false)
abstract class LibraryDb : RoomDatabase() {
    abstract fun dao(): LibraryDao
    companion object {
        fun open(ctx: Context) = Room.databaseBuilder(ctx, LibraryDb::class.java, "library.db").build()
    }
}
