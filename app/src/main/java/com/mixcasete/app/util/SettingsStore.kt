package com.mixcasete.app.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Preferencias simples del usuario: volumen, tono, balance, repetir, aleatorio, calibración. */
class SettingsStore(private val context: Context) {

    companion object {
        val KEY_VOLUME = floatPreferencesKey("volume")           // 0..1
        val KEY_TONE = intPreferencesKey("tone")                 // -10..10
        val KEY_BALANCE = intPreferencesKey("balance")           // -10..10
        val KEY_REPEAT = intPreferencesKey("repeat")             // 0 OFF, 1 ONE, 2 ALL
        val KEY_SHUFFLE = booleanPreferencesKey("shuffle")
        val KEY_CALIBRATION = booleanPreferencesKey("calibration")
        val KEY_LOGGED_IN = booleanPreferencesKey("yt_logged_in")
    }

    data class Settings(
        val volume: Float = 0.8f,
        val tone: Int = 0,
        val balance: Int = 0,
        val repeatModeIndex: Int = 0,
        val shuffle: Boolean = false,
        val calibration: Boolean = false,
        val ytLoggedIn: Boolean = false
    )

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            volume = p[KEY_VOLUME] ?: 0.8f,
            tone = p[KEY_TONE] ?: 0,
            balance = p[KEY_BALANCE] ?: 0,
            repeatModeIndex = p[KEY_REPEAT] ?: 0,
            shuffle = p[KEY_SHUFFLE] ?: false,
            calibration = p[KEY_CALIBRATION] ?: false,
            ytLoggedIn = p[KEY_LOGGED_IN] ?: false
        )
    }

    suspend fun setVolume(v: Float) = context.dataStore.edit { it[KEY_VOLUME] = v.coerceIn(0f, 1f) }
    suspend fun setTone(t: Int) = context.dataStore.edit { it[KEY_TONE] = t.coerceIn(-10, 10) }
    suspend fun setBalance(b: Int) = context.dataStore.edit { it[KEY_BALANCE] = b.coerceIn(-10, 10) }
    suspend fun setRepeat(i: Int) = context.dataStore.edit { it[KEY_REPEAT] = i }
    suspend fun setShuffle(s: Boolean) = context.dataStore.edit { it[KEY_SHUFFLE] = s }
    suspend fun setCalibration(c: Boolean) = context.dataStore.edit { it[KEY_CALIBRATION] = c }
    suspend fun setYtLoggedIn(l: Boolean) = context.dataStore.edit { it[KEY_LOGGED_IN] = l }
}
