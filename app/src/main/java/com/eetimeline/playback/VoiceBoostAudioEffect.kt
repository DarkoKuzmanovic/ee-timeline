package com.eetimeline.playback

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build

/** Applies speech-oriented gain and dynamics processing to an audio session. */
class VoiceBoostAudioEffect {
    private var enabled = false
    private var audioSessionId = INVALID_AUDIO_SESSION_ID
    private var effect: AudioEffect? = null

    /** Enables or disables voice boost, attaching it to [audioSessionId] when valid. */
    @Synchronized
    fun setEnabled(enabled: Boolean, audioSessionId: Int) {
        if (!enabled) {
            this.enabled = false
            this.audioSessionId = audioSessionId
            releaseEffect()
            return
        }

        if (this.enabled && this.audioSessionId == audioSessionId) return

        this.enabled = true
        this.audioSessionId = audioSessionId
        releaseEffect()
        if (audioSessionId <= 0) return

        effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            createDynamicsProcessing(audioSessionId) ?: createLoudnessEnhancer(audioSessionId)
        } else {
            createLoudnessEnhancer(audioSessionId)
        }
    }

    /** Reattaches the enabled effect when the player's audio session changes. */
    @Synchronized
    fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (this.audioSessionId == audioSessionId) return

        this.audioSessionId = audioSessionId
        if (!enabled) return

        releaseEffect()
        if (audioSessionId <= 0) return

        effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            createDynamicsProcessing(audioSessionId) ?: createLoudnessEnhancer(audioSessionId)
        } else {
            createLoudnessEnhancer(audioSessionId)
        }
    }

    /** Disables voice boost and releases the attached platform effect. */
    @Synchronized
    fun release() {
        enabled = false
        audioSessionId = INVALID_AUDIO_SESSION_ID
        releaseEffect()
    }

    private fun createDynamicsProcessing(audioSessionId: Int): AudioEffect? {
        var processing: DynamicsProcessing? = null
        return try {
            val compressorBand = DynamicsProcessing.MbcBand(
                true,
                20_000f,
                10f,
                180f,
                2f,
                -20f,
                6f,
                -90f,
                1f,
                0f,
                0f,
            )
            val compressor = DynamicsProcessing.Mbc(true, true, 1).apply {
                setBand(0, compressorBand)
            }
            val limiter = DynamicsProcessing.Limiter(
                true,
                true,
                0,
                5f,
                150f,
                10f,
                -1f,
                0f,
            )
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                CHANNEL_COUNT,
                false,
                0,
                true,
                1,
                false,
                0,
                true,
            )
                .setInputGainAllChannelsTo(INPUT_GAIN_DB)
                .setMbcAllChannelsTo(compressor)
                .setLimiterAllChannelsTo(limiter)
                .build()

            processing = DynamicsProcessing(0, audioSessionId, config)
            processing.enabled = true
            processing
        } catch (_: RuntimeException) {
            processing?.let(::releaseEffect)
            null
        }
    }

    private fun createLoudnessEnhancer(audioSessionId: Int): AudioEffect? {
        var enhancer: LoudnessEnhancer? = null
        return try {
            enhancer = LoudnessEnhancer(audioSessionId)
            enhancer.setTargetGain(FALLBACK_GAIN_MILLIBELS)
            enhancer.enabled = true
            enhancer
        } catch (_: RuntimeException) {
            enhancer?.let(::releaseEffect)
            null
        }
    }

    private fun releaseEffect() {
        val currentEffect = effect ?: return
        effect = null
        releaseEffect(currentEffect)
    }

    private fun releaseEffect(effect: AudioEffect) {
        try {
            effect.release()
        } catch (_: RuntimeException) {
            // Audio effects may already have been torn down by the platform.
        }
    }

    private companion object {
        const val CHANNEL_COUNT = 2
        const val INPUT_GAIN_DB = 2f
        const val FALLBACK_GAIN_MILLIBELS = 200
        const val INVALID_AUDIO_SESSION_ID = 0
    }
}
