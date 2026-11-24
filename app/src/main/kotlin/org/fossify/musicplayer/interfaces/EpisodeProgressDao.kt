package org.fossify.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.musicplayer.models.EpisodeProgress

@Dao
interface EpisodeProgressDao {
    @Query("SELECT * FROM episode_progress")
    fun getAll(): List<EpisodeProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(progress: EpisodeProgress)
}
