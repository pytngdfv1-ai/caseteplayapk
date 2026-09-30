package com.mixcasete.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val youtubeId: String?,
    val localUri: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val position: Int,
    val favorite: Boolean
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY position ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Insert
    suspend fun insert(track: TrackEntity)

    @Update
    suspend fun update(track: TrackEntity)

    @Delete
    suspend fun delete(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE tracks SET position = :pos WHERE id = :id")
    suspend fun setPosition(id: String, pos: Int)

    @Query("UPDATE tracks SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("SELECT MAX(position) FROM tracks")
    suspend fun maxPosition(): Int?
}

@Database(entities = [TrackEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, AppDb::class.java, "mixcasete.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
