package com.bipminder.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.LocalTime

@Composable
fun HomeScreen(
    all: List<Item>, s: AppSettings, warn: String?, onFix: () -> Unit,
    onOpen: (Item) -> Unit, onToggle: (Item) -> Unit, onDuplicate: (Item) -> Unit, onDelete: (Item) -> Unit
) {
    val now = System.currentTimeMillis()
    val t = today()
    val todays = remember(all, t) { occurrences(all, t, t) }
    val acts = all.filter { it.type != Type.NOTE }
    val overdue = acts.count { it.isOverdue(now) }
    val pending = acts.count { !it.completed && !it.isOverdue(now) }
    val a = millisAt(t, 0)
    val b = millisAt(t + 1, 0)
    val doneToday = acts.count { it.completed && it.completedAt in a until b }
    val h = LocalTime.now().hour
    val hello = when { h < 12 -> "Good morning"; h < 18 -> "Good afternoon"; else -> "Good evening" }
    val who = if (s.name.isBlank()) "" else ", ${s.name.trim()}"

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                BText("$hello$who", 15, 400, C.muted)
                Spacer(Modifier.height(6.dp))
                BText("Start today's tasks", 36, 300)
            }
        }
        if (warn != null) {
            item {
                BCard(Modifier.fillMaxWidth(), radius = 18, onClick = onFix) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warning, null, tint = Pri.med, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            BText(warn, 13, 400)
                            BText("Tap to fix in Settings", 12, 500, C.accent, Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(Modifier.weight(1f), todays.size.toString(), "Today", Icons.Outlined.Today)
                StatCard(Modifier.weight(1f), pending.toString(), "Pending", Icons.Outlined.Schedule)
                StatCard(Modifier.weight(1f), doneToday.toString(), "Done", Icons.Outlined.TaskAlt)
                StatCard(Modifier.weight(1f), overdue.toString(), "Overdue", Icons.Outlined.ErrorOutline)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BText("Today's schedule", 19, 500)
                BText(fullDate(t), 13, 400, C.muted)
            }
        }
        if (todays.isEmpty()) {
            item { EmptyState(Icons.Outlined.Event, "A clear day", "Nothing scheduled for today. Tap + to add a task, event or note.") }
        } else {
            items(todays, key = { "${it.item.id}-${it.day}" }) { occ ->
                ItemCard(
                    occ.item, occ.day, false, occ.item.recurrence == Rec.NONE || occ.day == occ.item.day,
                    { onOpen(occ.item) }, { onToggle(occ.item) }, { onDuplicate(occ.item) }, { onDelete(occ.item) }
                )
            }
        }
    }
}
