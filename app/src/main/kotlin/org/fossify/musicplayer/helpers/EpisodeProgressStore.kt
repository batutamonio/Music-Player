package org.fossify.musicplayer.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.episodeProgressDAO
import org.fossify.musicplayer.models.EpisodeProgress
import org.fossify.musicplayer.models.Track
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Guarda en memoria y en la base de datos el progreso de los episodios de carpetas marcables.
 * - Las listas leen de la memoria (rápido, sin tocar la base de datos al hacer scroll).
 * - Las escrituras van a la base de datos en un hilo aparte y en orden.
 * - Cuando algo cambia, avisa a las listas abiertas para que repinten esa fila.
 */
object EpisodeProgressStore {
    const val LISTENED_THRESHOLD = 0.95

    private const val CHECK = "✓ "
    private const val CLOCK = "🕐 "

    private val cache = ConcurrentHashMap<String, EpisodeProgress>()
    private val listeners = CopyOnWriteArrayList<(String?) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dbExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var loadRequested = false

    fun load(context: Context) {
        if (loadRequested) return
        loadRequested = true
        val appContext = context.applicationContext
        dbExecutor.execute {
            try {
                appContext.episodeProgressDAO.getAll().forEach { cache.putIfAbsent(it.path, it) }
            } catch (ignored: Exception) {
            }
            notifyListeners(null)
        }
    }

    fun get(path: String): EpisodeProgress? = cache[path]

    /** Llamado por el reproductor cada pocos segundos, al pausar y al cambiar de pista. */
    fun savePosition(context: Context, path: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0 || positionMs < 0) return
        val old = cache[path]
        val updated = if (positionMs >= durationMs * LISTENED_THRESHOLD) {
            EpisodeProgress(path, 0, true, System.currentTimeMillis())
        } else {
            EpisodeProgress(path, (positionMs / 1000).toInt(), old?.listened ?: false, System.currentTimeMillis())
        }

        if (old != null && old.position == updated.position && old.listened == updated.listened) return
        write(context, updated)
    }

    /** El episodio ha terminado de sonar entero. */
    fun markFinished(context: Context, path: String) {
        val old = cache[path]
        if (old != null && old.listened && old.position == 0) return
        write(context, EpisodeProgress(path, 0, true, System.currentTimeMillis()))
    }

    /** Marcar / desmarcar a mano. En ambos casos el minuto vuelve a 0:00. */
    fun setListened(context: Context, paths: Collection<String>, listened: Boolean) {
        paths.forEach { write(context, EpisodeProgress(it, 0, listened, System.currentTimeMillis())) }
    }

    /** Minuto (en ms) donde reanudar, o null si hay que empezar como siempre. */
    fun getResumePositionMs(path: String, durationSeconds: Int): Long? {
        val progress = cache[path] ?: return null
        // al terminar o marcar/desmarcar, el minuto vuelve a 0: si hay minuto guardado es que estás a medias
        if (progress.position <= 0) return null
        if (durationSeconds > 0 && progress.position >= durationSeconds * LISTENED_THRESHOLD) return null
        return progress.position * 1000L
    }

    // ---------- lo que se ve en las listas ----------

    fun getTitlePrefix(context: Context, track: Track): String {
        val progress = getVisibleProgress(context, track) ?: return ""
        var prefix = ""
        if (progress.listened) prefix += CHECK
        if (progress.position > 0) prefix += CLOCK
        return prefix
    }

    fun getDurationText(context: Context, track: Track): String {
        val duration = track.duration.getFormattedDuration()
        val progress = getVisibleProgress(context, track) ?: return duration
        return if (progress.position > 0) "${progress.position.getFormattedDuration()} / $duration" else duration
    }

    private fun getVisibleProgress(context: Context, track: Track): EpisodeProgress? {
        val progress = cache[track.path] ?: return null
        return if (context.config.isPathMarkable(track.path)) progress else null
    }

    // ---------- avisos a las listas ----------

    fun addListener(listener: (String?) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String?) -> Unit) {
        listeners.remove(listener)
    }

    private fun write(context: Context, progress: EpisodeProgress) {
        cache[progress.path] = progress
        val appContext = context.applicationContext
        dbExecutor.execute {
            try {
                appContext.episodeProgressDAO.insertOrUpdate(progress)
            } catch (ignored: Exception) {
            }
        }
        notifyListeners(progress.path)
    }

    /** path == null significa "repintar todo". */
    private fun notifyListeners(path: String?) {
        mainHandler.post { listeners.forEach { it(path) } }
    }
}
