package com.bipminder.app

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.first
import java.util.Locale

object Repo {
    private fun dao(c: Context) = AppDb.get(c).dao()

    suspend fun save(ctx: Context, item: Item): Long {
        val c = ctx.applicationContext
        val id = if (item.id == 0L) dao(c).insert(item) else { dao(c).update(item); item.id }
        Alarms.scheduleItem(c, item.copy(id = id), c.settingsFlow().first())
        return id
    }

    suspend fun setCompleted(ctx: Context, item: Item, done: Boolean) {
        val c = ctx.applicationContext
        val s = c.settingsFlow().first()
        val now = System.currentTimeMillis()
        if (!done) {
            val u = item.copy(completed = false, completedAt = 0)
            dao(c).update(u); Alarms.scheduleItem(c, u, s); return
        }
        if (item.recurrence != Rec.NONE) {
            dao(c).insert(item.copy(id = 0, recurrence = Rec.NONE, completed = true, completedAt = now,
                reminders = "", snoozeUntil = 0))
            val nd = item.nextStartAfter(item.day)
            val u = item.copy(day = nd, endDay = nd + item.span(), snoozeUntil = 0)
            dao(c).update(u); Alarms.scheduleItem(c, u, s)
        } else {
            val u = item.copy(completed = true, completedAt = now, snoozeUntil = 0)
            dao(c).update(u); Alarms.cancelItem(c, u.id)
        }
        NotificationManagerCompat.from(c).cancel(item.id.toInt())
    }

    suspend fun toggle(ctx: Context, item: Item) = setCompleted(ctx, item, !item.completed)

    suspend fun complete(ctx: Context, id: Long) {
        val it = dao(ctx.applicationContext).get(id) ?: return
        if (!it.completed) setCompleted(ctx, it, true)
    }

    suspend fun snooze(ctx: Context, id: Long, minutes: Int) {
        val c = ctx.applicationContext
        val it = dao(c).get(id) ?: return
        val u = it.copy(snoozeUntil = System.currentTimeMillis() + minutes * 60_000L)
        dao(c).update(u)
        Alarms.scheduleItem(c, u, c.settingsFlow().first())
    }

    suspend fun delete(ctx: Context, item: Item) {
        val c = ctx.applicationContext
        dao(c).delete(item)
        Alarms.cancelItem(c, item.id)
        NotificationManagerCompat.from(c).cancel(item.id.toInt())
    }

    suspend fun restore(ctx: Context, item: Item) {
        val c = ctx.applicationContext
        dao(c).insert(item)
        Alarms.scheduleItem(c, item, c.settingsFlow().first())
    }

    suspend fun duplicate(ctx: Context, item: Item) {
        save(ctx, item.copy(id = 0, title = item.title + " (copy)", completed = false, completedAt = 0,
            createdAt = System.currentTimeMillis(), snoozeUntil = 0))
    }
}

fun defaultReminders(s: AppSettings): String = if (s.defaultReminder < 0) "" else s.defaultReminder.toString()

/** Parses quick-add text such as "Call Sam tomorrow at 5pm" or "Stretch in 30 minutes". */
fun parseQuick(text: String, type: Int, baseDay: Long, s: AppSettings): Item {
    var t = text.trim()
    var day = baseDay
    var dayWord = false
    var min: Int? = null
    if (type != Type.NOTE) {
        if (Regex("(?i)\\btomorrow\\b").containsMatchIn(t)) {
            day = today() + 1; dayWord = true
            t = t.replace(Regex("(?i)\\btomorrow\\b"), "")
        } else if (Regex("(?i)\\btoday\\b").containsMatchIn(t)) {
            day = today(); dayWord = true
            t = t.replace(Regex("(?i)\\btoday\\b"), "")
        }
        val rel = Regex("(?i)\\bin\\s+(\\d{1,3})\\s*(minutes|minute|mins|min|hours|hour|hrs|hr|h)\\b").find(t)
        val ampm = Regex("(?i)\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b").find(t)
        val h24 = Regex("(?i)\\bat\\s+(\\d{1,2}):(\\d{2})\\b").find(t)
        if (rel != null) {
            val n = rel.groupValues[1].toInt()
            val mins = if (rel.groupValues[2].lowercase().startsWith("h")) n * 60 else n
            val target = java.time.LocalDateTime.now().plusMinutes(mins.toLong())
            day = target.toLocalDate().toEpochDay(); dayWord = true
            min = target.hour * 60 + target.minute
            t = t.replace(rel.value, "")
        } else if (ampm != null) {
            var h = ampm.groupValues[1].toInt() % 12
            if (ampm.groupValues[3].lowercase() == "pm") h += 12
            min = h * 60 + (ampm.groupValues[2].toIntOrNull() ?: 0)
            t = t.replace(ampm.value, "")
        } else if (h24 != null) {
            min = (h24.groupValues[1].toInt().coerceIn(0, 23)) * 60 + h24.groupValues[2].toInt().coerceIn(0, 59)
            t = t.replace(h24.value, "")
        }
        if (min != null && !dayWord && day == today() && millisAt(day, min) < System.currentTimeMillis()) day += 1
    }
    t = t.replace(Regex("\\s+"), " ").trim().ifEmpty { text.trim() }
    val timed = min != null
    val start = min ?: 540
    return Item(
        type = type, title = t, day = day, endDay = day,
        allDay = !timed && type != Type.NOTE,
        startMin = start, endMin = minOf(1439, start + 60),
        reminders = if (type == Type.NOTE) "" else defaultReminders(s)
    )
}

fun previewQuick(i: Item): String =
    if (i.type == Type.NOTE) "Saved as a note" else "${dayLabel(i.day)} · ${if (i.allDay) "All day" else fmtTime(i.startMin)}"

fun normalizeTags(raw: String): String =
    raw.split(',', ' ', '\n').map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }
        .distinct().joinToString(",") { it.lowercase(Locale.getDefault()) }
