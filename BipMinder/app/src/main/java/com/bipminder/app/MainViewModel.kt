package com.bipminder.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.applicationContext

    val items: StateFlow<List<Item>> =
        AppDb.get(c).dao().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<AppSettings> =
        c.settingsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val undo = MutableSharedFlow<Item>(extraBufferCapacity = 8)
    val toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    var seed: Item? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val used = AppDb.get(c).dao().allNow().map { it.photo }.toSet()
            Backup.cleanOrphans(c, used)
            Alarms.rescheduleAll(c)
        }
    }

    fun save(item: Item, done: () -> Unit = {}) = viewModelScope.launch {
        Repo.save(c, item); done()
    }
    fun toggle(item: Item) { viewModelScope.launch { Repo.toggle(c, item) } }
    fun duplicate(item: Item) { viewModelScope.launch { Repo.duplicate(c, item); toast.emit("Duplicated") } }
    fun delete(item: Item, done: () -> Unit = {}) {
        viewModelScope.launch { Repo.delete(c, item); undo.emit(item); done() }
    }
    fun restore(item: Item) { viewModelScope.launch { Repo.restore(c, item) } }
    fun updateSettings(f: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val n = f(settings.value)
            c.saveSettings(n)
            Alarms.rescheduleAll(c)
        }
    }
    fun export(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try { toast.emit("Backup saved (${Backup.export(c, uri)} items)") }
            catch (e: Exception) { toast.emit("Backup failed: ${e.message}") }
        }
    }
    fun import(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try { toast.emit("Restored ${Backup.import(c, uri)} items") }
            catch (e: Exception) { toast.emit("Restore failed: ${e.message}") }
        }
    }
}
