package com.bipminder.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bip_settings")

data class AppSettings(
    val theme: Int = 0,            // 0 system, 1 light, 2 dark
    val snooze: Int = 10,
    val defaultReminder: Int = 0,  // -1 none
    val allDayMin: Int = 540,
    val notifications: Boolean = true,
    val weekMonday: Boolean = true,
    val name: String = ""
)

private object K {
    val theme = intPreferencesKey("theme")
    val snooze = intPreferencesKey("snooze")
    val defRem = intPreferencesKey("def_rem")
    val allDay = intPreferencesKey("all_day_min")
    val notif = booleanPreferencesKey("notif")
    val monday = booleanPreferencesKey("monday")
    val name = stringPreferencesKey("name")
}

fun Context.settingsFlow(): Flow<AppSettings> = applicationContext.dataStore.data.map { p ->
    AppSettings(
        theme = p[K.theme] ?: 0,
        snooze = p[K.snooze] ?: 10,
        defaultReminder = p[K.defRem] ?: 0,
        allDayMin = p[K.allDay] ?: 540,
        notifications = p[K.notif] ?: true,
        weekMonday = p[K.monday] ?: true,
        name = p[K.name] ?: ""
    )
}

suspend fun Context.saveSettings(s: AppSettings) {
    applicationContext.dataStore.edit { p ->
        p[K.theme] = s.theme
        p[K.snooze] = s.snooze
        p[K.defRem] = s.defaultReminder
        p[K.allDay] = s.allDayMin
        p[K.notif] = s.notifications
        p[K.monday] = s.weekMonday
        p[K.name] = s.name
    }
}
