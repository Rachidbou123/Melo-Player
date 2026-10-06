package com.example.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log

class AudioEffectManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("melo_audio_effects", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentSessionId: Int = 0

    var isEqEnabled: Boolean
        get() = prefs.getBoolean("eq_enabled", true)
        set(value) {
            prefs.edit().putBoolean("eq_enabled", value).apply()
            try {
                equalizer?.enabled = value
            } catch (e: Exception) {
                Log.w("AudioEffectManager", "Failed to toggle EQ", e)
            }
        }

    var isBassBoostEnabled: Boolean
        get() = prefs.getBoolean("bass_boost_enabled", false)
        set(value) {
            prefs.edit().putBoolean("bass_boost_enabled", value).apply()
            try {
                bassBoost?.enabled = value
            } catch (e: Exception) {
                Log.w("AudioEffectManager", "Failed to toggle BassBoost", e)
            }
        }

    var bassBoostStrength: Short
        get() = prefs.getInt("bass_boost_strength", 500).toShort()
        set(value) {
            prefs.edit().putInt("bass_boost_strength", value.toInt()).apply()
            try {
                if (bassBoost?.strengthSupported == true) {
                    bassBoost?.setStrength(value)
                }
            } catch (e: Exception) {
                Log.w("AudioEffectManager", "Failed to set BassBoost strength", e)
            }
        }

    var currentPreset: String
        get() = prefs.getString("eq_preset", "Flat") ?: "Flat"
        set(value) {
            prefs.edit().putString("eq_preset", value).apply()
            applyPreset(value)
        }

    val availablePresets: List<String> = listOf(
        "Flat", "Bass", "Bass Boost", "Vocal", "Rock", "Pop", "Hip-Hop", "Classical", "Electronic"
    )

    fun attachSession(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (currentSessionId == audioSessionId && equalizer != null) return

        release()
        currentSessionId = audioSessionId

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEqEnabled
            }
            applyPreset(currentPreset)
        } catch (e: Exception) {
            Log.e("AudioEffectManager", "Equalizer creation failed for session $audioSessionId", e)
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = isBassBoostEnabled
                if (strengthSupported) {
                    setStrength(bassBoostStrength)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioEffectManager", "BassBoost creation failed for session $audioSessionId", e)
        }
    }

    fun applyPreset(presetName: String) {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            if (numBands <= 0) return

            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]

            // Map standard presets to relative band percentage values [-100..100]
            val percentages = when (presetName) {
                "Bass", "Bass Boost" -> floatArrayOf(80f, 60f, 20f, 0f, -20f)
                "Vocal" -> floatArrayOf(-20f, 0f, 70f, 60f, 10f)
                "Rock" -> floatArrayOf(60f, 30f, -10f, 40f, 70f)
                "Pop" -> floatArrayOf(-10f, 40f, 70f, 30f, -10f)
                "Hip-Hop" -> floatArrayOf(80f, 50f, -10f, 20f, 40f)
                "Classical" -> floatArrayOf(50f, 30f, -10f, 30f, 60f)
                "Electronic" -> floatArrayOf(70f, 40f, -10f, 50f, 80f)
                else -> floatArrayOf(0f, 0f, 0f, 0f, 0f) // Flat
            }

            for (i in 0 until numBands) {
                val p = percentages.getOrElse(i) { 0f }
                val targetLevel = if (p >= 0) {
                    (p / 100f * maxLevel).toInt().toShort()
                } else {
                    (-p / 100f * minLevel).toInt().toShort()
                }
                eq.setBandLevel(i.toShort(), targetLevel)
            }
        } catch (e: Exception) {
            Log.w("AudioEffectManager", "Failed to apply EQ preset $presetName", e)
        }
    }

    fun setCustomBandLevel(bandIndex: Short, level: Short) {
        try {
            equalizer?.setBandLevel(bandIndex, level)
            prefs.edit().putInt("band_level_$bandIndex", level.toInt()).apply()
        } catch (e: Exception) {
            Log.w("AudioEffectManager", "Failed to set custom band level", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            equalizer = null
        }

        try {
            bassBoost?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            bassBoost = null
        }
    }
}
