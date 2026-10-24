package com.bipminder.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun CalendarScreen(
    all: List<Item>, s: AppSettings, sel: Long, onSel: (Long) -> Unit,
    onOpen: (Item) -> Unit, onToggle: (Item) -> Unit, onDuplicate: (Item) -> Unit, onDelete: (Item) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(2) } // 0 day, 1 week, 2 month
    val first = ld(sel).withDayOfMonth(1)
    val from = first.toEpochDay() - 7
    val to = first.toEpochDay() + first.lengthOfMonth() + 7
    val occs = remember(all, from, to) { occurrences(all, from, to) }
    val byDay = remember(occs) { bucket(occs, from, to) }
    val dayList = byDay[sel] ?: emptyList()
    val t = today()

    fun move(dir: Int) {
        onSel(when (mode) {
            0 -> sel + dir
            1 -> sel + 7L * dir
            else -> ld(sel).plusMonths(dir.toLong()).toEpochDay()
        })
    }
    val title = when (mode) {
        0 -> fullDate(sel)
        1 -> { val w = weekStart(sel, s.weekMonday); "${shortDate(w)} – ${shortDate(w + 6)}" }
        else -> monthTitle(sel)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                ScreenTitle("Calendar", 0) { CircleBtn(Icons.Outlined.Today, { onSel(t) }, desc = "Today") }
                Row(Modifier.padding(horizontal = 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Day", "Week", "Month").forEachIndexed { i, n -> Pill(n, mode == i, { mode = i }) }
                }
            }
        }
        item {
            BCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        CircleBtn(Icons.Outlined.ChevronLeft, { move(-1) }, size = 38)
                        BText(title, 17, 500)
                        CircleBtn(Icons.Outlined.ChevronRight, { move(1) }, size = 38)
                    }
                    Spacer(Modifier.height(10.dp))
                    when (mode) {
                        0 -> {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                                BText("${dayList.size} scheduled", 13, 400, C.muted)
                            }
                        }
                        1 -> {
                            val w = weekStart(sel, s.weekMonday)
                            Row(Modifier.fillMaxWidth()) {
                                for (i in 0..6) {
                                    val d = w + i
                                    DayCell(Modifier.weight(1f), d, d == sel, d == t, byDay[d] ?: emptyList(), true) { onSel(d) }
                                }
                            }
                        }
                        else -> MonthGrid(first.toEpochDay(), sel, t, byDay, s.weekMonday, onSel)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                BText(dayLabel(sel), 19, 500)
                BText("${dayList.size} item${if (dayList.size == 1) "" else "s"}", 13, 400, C.muted)
            }
        }
        if (dayList.isEmpty()) {
            item { EmptyState(Icons.Outlined.Event, "Free day", "No events or tasks on this date.") }
        } else {
            items(dayList, key = { "${it.item.id}-${it.day}" }) { occ ->
                ItemCard(
                    occ.item, occ.day, false, occ.item.recurrence == Rec.NONE || occ.day == occ.item.day,
                    { onOpen(occ.item) }, { onToggle(occ.item) }, { onDuplicate(occ.item) }, { onDelete(occ.item) }
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier, day: Long, selected: Boolean, isToday: Boolean,
    occs: List<Occ>, showWeekday: Boolean, onClick: () -> Unit
) {
    Column(
        modifier.height(if (showWeekday) 78.dp else 50.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        if (showWeekday) { BText(dowShort(day).take(3), 11, 400, C.muted); Spacer(Modifier.height(4.dp)) }
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (selected) C.accent else androidx.compose.ui.graphics.Color.Transparent)
                .then(if (isToday && !selected) Modifier.border(1.5.dp, C.accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) { BText(ld(day).dayOfMonth.toString(), 14, 500, if (selected) C.onAccent else C.text) }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
            occs.take(3).forEach { o ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (o.item.completed) C.muted else Pri.color(o.item.priority)))
            }
        }
    }
}

@Composable
private fun MonthGrid(first: Long, sel: Long, t: Long, byDay: Map<Long, List<Occ>>, monday: Boolean, onSel: (Long) -> Unit) {
    val f = ld(first)
    val offset = if (monday) f.dayOfWeek.value - 1 else f.dayOfWeek.value % 7
    val len = f.lengthOfMonth()
    val rows = (offset + len + 6) / 7
    Column {
        Row(Modifier.fillMaxWidth()) {
            for (c in 0..6) {
                val dow = if (monday) c + 1 else (if (c == 0) 7 else c)
                BText(narrowDow(dow), 12, 500, C.muted, Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(6.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val n = r * 7 + c - offset + 1
                    if (n in 1..len) {
                        val d = first + n - 1
                        DayCell(Modifier.weight(1f), d, d == sel, d == t, byDay[d] ?: emptyList(), false) { onSel(d) }
                    } else Spacer(Modifier.weight(1f).height(50.dp))
                }
            }
        }
    }
}
