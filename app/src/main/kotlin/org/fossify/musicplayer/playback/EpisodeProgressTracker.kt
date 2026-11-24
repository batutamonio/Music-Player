package org.fossify.musicplayer.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.toTrack
import org.fossify.musicplayer.helpers.EpisodeProgressStore
import kotlin.math.abs

/**
 * Sigue lo que suena y, si el tema está en una carpeta marcable:
 * - guarda el minuto cada pocos segundos, al pausar, al saltar y al cambiar de pista,
 * - lo marca como escuchado al pasar del 95 % o al terminar,
 * - al empezar un episodio a medias, salta al minuto donde te quedaste.
 * El reproductor vive en el hilo principal, así que todo esto corre ahí.
 */
@UnstableApi
class EpisodeProgressTracker(private val service: PlaybackService) {
    private val handler = Handler(Looper.getMainLooper())

    private var currentPath: String? = null
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            saveCurrent()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(tick)
        if (isPlaying) {
            handler.postDelayed(tick, SAVE_INTERVAL_MS)
        } else {
            saveCurrent()
        }
    }

    fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val previousPath = currentPath
        if (previousPath != null && isMarkable(previousPath)) {
            val finishedNaturally = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            if (finishedNaturally) {
                EpisodeProgressStore.markFinished(service, previousPath)
            } else if (lastDurationMs > 0) {
                EpisodeProgressStore.savePosition(service, previousPath, lastPositionMs, lastDurationMs)
            }
        }

        val track = mediaItem?.toTrack()
        currentPath = track?.path
        lastPositionMs = 0L
        lastDurationMs = 0L

        // reanudar donde te quedaste (no al repetir: ahí acaba de terminar)
        if (track != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && isMarkable(track.path)) {
            val resumeAtMs = EpisodeProgressStore.getResumePositionMs(track.path, track.duration) ?: return
            if (abs(service.player.currentPosition - resumeAtMs) > RESUME_TOLERANCE_MS) {
                service.player.seekTo(resumeAtMs)
            }
        }
    }

    fun onPositionDiscontinuity(reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            saveCurrent()
        }
    }

    fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) {
            val path = currentPath ?: return
            if (isMarkable(path)) {
                EpisodeProgressStore.markFinished(service, path)
            }
        }
    }

    fun onServiceDestroyed() {
        handler.removeCallbacks(tick)
        saveCurrent()
    }

    private fun saveCurrent() {
        try {
            val player = service.player
            val path = currentPath ?: player.currentMediaItem?.toTrack()?.path?.also { currentPath = it } ?: return
            val durationMs = player.duration
            if (durationMs <= 0) return

            lastPositionMs = player.currentPosition
            lastDurationMs = durationMs
            if (isMarkable(path)) {
                EpisodeProgressStore.savePosition(service, path, lastPositionMs, lastDurationMs)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun isMarkable(path: String) = service.config.isPathMarkable(path)

    companion object {
        private const val SAVE_INTERVAL_MS = 5_000L
        private const val RESUME_TOLERANCE_MS = 3_000L
    }
}
