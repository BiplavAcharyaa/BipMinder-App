package com.bipminder.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt { var h24 = false }

fun today(): Long = LocalDate.now().toEpochDay()
fun ld(day: Long): LocalDate = LocalDate.ofEpochDay(day)

fun millisAt(day: Long, min: Int): Long {
    val m = min.coerceIn(0, 1440)
    return LocalDate.ofEpochDay(day).atStartOfDay().plusMinutes(m.toLong())
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

fun fmtTime(min: Int): String {
    val h = (min / 60) % 24
    val m = min % 60
    return if (Fmt.h24) String.format(Locale.US, "%02d:%02d", h, m)
    else String.format(Locale.US, "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
}

fun fmtHour(h: Int): String =
    if (Fmt.h24) String.format(Locale.US, "%02d", h) else "${if (h % 12 == 0) 12 else h % 12}${if (h < 12) "a" else "p"}"

fun dayLabel(day: Long): String {
    val t = today()
    return when (day) {
        t -> "Today"
        t + 1 -> "Tomorrow"
        t - 1 -> "Yesterday"
        else -> ld(day).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    }
}
fun shortDate(day: Long): String = ld(day).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
fun fullDate(day: Long): String = ld(day).format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
fun monthTitle(day: Long): String = ld(day).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
fun dowShort(day: Long): String = ld(day).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())

fun weekStart(day: Long, monday: Boolean): Long {
    val dow = ld(day).dayOfWeek.value
    val off = if (monday) dow - 1 else dow % 7
    return day - off
}

fun narrowDow(dow: Int): String =
    DayOfWeek.of(dow).getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())

// ---- Item time logic ----
fun Item.span(): Long = endDay - day

fun Item.startsOn(s: Long): Boolean {
    if (s < day) return false
    return when (recurrence) {
        Rec.NONE -> s == day
        Rec.DAILY -> true
        Rec.WEEKLY -> (s - day) % 7 == 0L
        Rec.WEEKDAYS -> ld(s).dayOfWeek.value in 1..5
        Rec.MONTHLY -> {
            val dom = ld(day).dayOfMonth
            val l = ld(s)
            l.dayOfMonth == minOf(dom, l.lengthOfMonth())
        }
        else -> false
    }
}

fun Item.nextStartAfter(from: Long): Long {
    var s = from + 1
    repeat(800) { if (startsOn(s)) return s; s++ }
    return from + 1
}

fun Item.endMillis(): Long = millisAt(endDay, if (allDay) 1440 else endMin)
fun Item.isOverdue(now: Long = System.currentTimeMillis()): Boolean =
    type == Type.TASK && !completed && endMillis() < now

fun Item.offsets(): List<Int> =
    reminders.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }.distinct().take(Alarms.MAX_REMINDERS)

fun Item.nextTrigger(offsetMin: Int, after: Long, allDayMin: Int): Long? {
    val sm = if (allDay) allDayMin else startMin
    if (recurrence == Rec.NONE) {
        val t = millisAt(day, sm) - offsetMin * 60_000L
        return if (t > after) t else null
    }
    var s = maxOf(day, today() - (offsetMin / 1440 + 2))
    repeat(800) {
        if (startsOn(s)) {
            val t = millisAt(s, sm) - offsetMin * 60_000L
            if (t > after) return t
        }
        s++
    }
    return null
}

fun Item.tagList(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

data class Occ(val item: Item, val day: Long, val endDay: Long) {
    val startMin: Int get() = if (item.allDay) 0 else item.startMin
    val endMin: Int get() = if (item.allDay) 1440 else item.endMin
}

fun occurrences(all: List<Item>, from: Long, to: Long): List<Occ> {
    val out = ArrayList<Occ>()
    for (item in all) {
        if (item.type == Type.NOTE) continue
        val span = maxOf(0L, item.endDay - item.day)
        if (item.recurrence == Rec.NONE) {
            if (item.day <= to && item.day + span >= from) out.add(Occ(item, item.day, item.day + span))
        } else {
            var s = maxOf(item.day, from - span)
            while (s <= to) {
                if (item.startsOn(s)) out.add(Occ(item, s, s + span))
                s++
            }
        }
    }
    return out.sortedWith(compareBy({ it.day }, { it.startMin }))
}

fun bucket(occs: List<Occ>, from: Long, to: Long): Map<Long, List<Occ>> {
    val m = HashMap<Long, MutableList<Occ>>()
    for (o in occs) {
        var d = maxOf(o.day, from)
        val e = minOf(o.endDay, to)
        while (d <= e) { m.getOrPut(d) { mutableListOf() }.add(o); d++ }
    }
    return m
}

fun recLabel(r: Int) = when (r) {
    Rec.DAILY -> "Daily"; Rec.WEEKLY -> "Weekly"; Rec.MONTHLY -> "Monthly"; Rec.WEEKDAYS -> "Weekdays"; else -> "Never"
}

fun reminderLabel(m: Int): String = when {
    m == 0 -> "At time"
    m % 1440 == 0 -> "${m / 1440} day${if (m / 1440 > 1) "s" else ""} before"
    m % 60 == 0 -> "${m / 60} hr before"
    else -> "$m min before"
}

fun timeLabel(item: Item): String {
    val base = if (item.allDay) "All day" else "${fmtTime(item.startMin)} – ${fmtTime(item.endMin)}"
    return if (item.endDay > item.day) "$base · to ${shortDate(item.endDay)}" else base
}
