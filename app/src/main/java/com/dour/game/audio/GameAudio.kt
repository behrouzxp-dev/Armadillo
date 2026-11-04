package com.dour.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.dour.game.R
import com.dour.game.game.SoundSettings

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
    private val activeStreams = mutableSetOf<Int>()

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
        beepStream = soundPool.play(beepId, scale(settings.beepVolume), scale(settings.beepVolume), 1, 0, 1f)
        if (beepStream != 0) activeStreams += beepStream
    }

    fun stopBeep() {
        val stream = beepStream
        if (stream != 0) soundPool.stop(stream)
        activeStreams.remove(stream)
        beepStream = 0
    }

    fun stopAll() {
        activeStreams.toList().forEach { if (it != 0) soundPool.stop(it) }
        activeStreams.clear()
        beepStream = 0
    }

    fun playExplosion() {
        if (!allowed(settings.explosionEnabled, settings.explosionVolume) || explosionId !in loadedIds) return
        soundPool.play(explosionId, scale(settings.explosionVolume), scale(settings.explosionVolume), 2, 0, 1f)
            .takeIf { it != 0 }?.let(activeStreams::add)
    }

    fun playEffect() {
        if (!allowed(settings.effectsEnabled, settings.effectsVolume) || effectId !in loadedIds) return
        soundPool.play(effectId, scale(settings.effectsVolume), scale(settings.effectsVolume), 1, 0, 1f)
            .takeIf { it != 0 }?.let(activeStreams::add)
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
            val streamAllows = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0 &&
                !audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
            ringAllows && streamAllows
        } catch (_: Exception) {
            false
        }
    }

    private fun scale(volume: Int): Float = volume.coerceIn(0, 100) / 100f
}
