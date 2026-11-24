package org.fossify.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Progreso de un episodio de una carpeta marcable.
 * Se identifica por la ruta del archivo: si se borra y se vuelve a descargar con
 * el mismo nombre y carpeta, recupera su estado.
 */
@Entity(tableName = "episode_progress")
data class EpisodeProgress(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "position") val position: Int,         // segundos donde te quedaste (0 = sin empezar o terminado)
    @ColumnInfo(name = "listened") val listened: Boolean,     // ✓
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
