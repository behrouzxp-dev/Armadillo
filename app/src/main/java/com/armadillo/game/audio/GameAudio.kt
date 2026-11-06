package com.armadillo.game.audio

import android.content.Context
import android.os.SystemClock
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.armadillo.game.R
import com.armadillo.game.game.SoundSettings

class GameAudio(context: Context, initialSettings: SoundSettings) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val loadedIds = mutableSetOf<Int>()
    private val beepId: Int
    private val explosionId: Int
    private val effectId: Int
    private var settings = initialSettings.copy()
    private var beepStream = 0
    /** Stream id to the elapsed-realtime millis at which it was started. */
    private val activeStreams = mutableMapOf<Int, Long>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds += sampleId
        }
        beepId = soundPool.load(context, R.raw.bomb_beep, 1)
        explosionId = soundPool.load(context, R.raw.bomb_explosion, 1)
        effectId = soundPool.load(context, R.raw.ui_sample, 1)
    }

    fun applySettings(updated: SoundSettings) {
        val changed = settings.beepEnabled != updated.beepEnabled ||
            settings.beepVolume != updated.beepVolume ||
            settings.explosionEnabled != updated.explosionEnabled ||
            settings.explosionVolume != updated.explosionVolume ||
            settings.effectsEnabled != updated.effectsEnabled ||
            settings.effectsVolume != updated.effectsVolume ||
            settings.muteAll != updated.muteAll
        settings = updated.copy()
        if (changed || settings.muteAll || !systemAllowsSound()) stopAll()
    }

    fun playBeep() {
        if (!allowed(settings.beepEnabled, settings.beepVolume) || beepId !in loadedIds) return
        pruneFinishedStreams()
        beepStream = soundPool.play(beepId, scale(settings.beepVolume), scale(settings.beepVolume), 1, 0, 1f)
        if (beepStream != 0) activeStreams[beepStream] = SystemClock.elapsedRealtime()
    }

    fun stopBeep() {
        val stream = beepStream
        if (stream != 0) soundPool.stop(stream)
        activeStreams.remove(stream)
        beepStream = 0
    }

    fun stopAll() {
        activeStreams.keys.toList().forEach { if (it != 0) soundPool.stop(it) }
        activeStreams.clear()
        beepStream = 0
    }

    /**
     * Streams that end naturally never report back, so without this the set would keep
     * growing for the whole game. Anything older than the longest effect is dropped.
     */
    private fun pruneFinishedStreams() {
        val cutoff = SystemClock.elapsedRealtime() - FINISHED_STREAM_TIMEOUT_MS
        activeStreams.entries.removeAll { (_, startedAt) -> startedAt < cutoff }
    }

    fun playExplosion() {
        if (!allowed(settings.explosionEnabled, settings.explosionVolume) || explosionId !in loadedIds) return
        pruneFinishedStreams()
        soundPool.play(explosionId, scale(settings.explosionVolume), scale(settings.explosionVolume), 2, 0, 1f)
            .takeIf { it != 0 }?.let { activeStreams[it] = SystemClock.elapsedRealtime() }
    }

    fun playEffect() {
        if (!allowed(settings.effectsEnabled, settings.effectsVolume) || effectId !in loadedIds) return
        pruneFinishedStreams()
        soundPool.play(effectId, scale(settings.effectsVolume), scale(settings.effectsVolume), 1, 0, 1f)
            .takeIf { it != 0 }?.let { activeStreams[it] = SystemClock.elapsedRealtime() }
    }

    fun previewBeep() = playBeep()
    fun previewExplosion() = playExplosion()
    fun previewEffect() = playEffect()

    fun release() {
        stopAll()
        soundPool.release()
    }

    private fun allowed(enabled: Boolean, volume: Int): Boolean =
        enabled && volume > 0 && !settings.muteAll && systemAllowsSound()

    private fun systemAllowsSound(): Boolean {
        return try {
            val ringAllows = audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL
            // A muted music stream reports volume 0; isStreamMute() was deprecated in API 23.
            val streamAllows = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
            ringAllows && streamAllows
        } catch (_: Exception) {
            false
        }
    }

    private fun scale(volume: Int): Float = volume.coerceIn(0, 100) / 100f

    private companion object {
        const val FINISHED_STREAM_TIMEOUT_MS = 5_000L
    }
}
