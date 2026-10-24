package com.bipminder.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ProgressScreen(all: List<Item>, s: AppSettings, onOpen: (Item) -> Unit) {
    var sel by rememberSaveable { mutableStateOf(today()) }
    val t = today()
    val ws = weekStart(sel, s.weekMonday)
    val week = (0..6).map { ws + it }
    val occs = remember(all, ws) { occurrences(all, ws - 1, ws + 7) }
    val by = remember(occs) { bucket(occs, ws, ws + 6) }
    val total = week.map { (by[it] ?: emptyList()).size }
    val done = week.map { d -> (by[d] ?: emptyList()).count { it.item.completed } }
    val sumT = total.sum()
    val sumD = done.sum()
    val rate = if (sumT == 0) 0 else sumD * 100 / sumT

    val streak = remember(all, t) {
        val o = occurrences(all, t - 400, t).filter { it.item.completed }
        val days = o.map { it.day }.toSet()
        var d = if (t in days) t else t - 1
        var n = 0
        while (d in days) { n++; d-- }
        n
    }
    val overdue = all.count { it.isOverdue() }
    val selList = by[sel] ?: emptyList()
    val selDone = selList.count { it.item.completed }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
        ScreenTitle("Progress") { CircleBtn(Icons.Outlined.Today, { sel = t }, desc = "Today") }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Week selector
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                CircleBtn(Icons.Outlined.ChevronLeft, { sel -= 7 }, size = 38)
                BText("${shortDate(ws)} – ${shortDate(ws + 6)}", 17, 500)
                CircleBtn(Icons.Outlined.ChevronRight, { sel += 7 }, size = 38)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { d ->
                    val isSel = d == sel
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { sel = d }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BText(dowShort(d).take(3), 11, 400, C.muted)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (isSel) C.accent else C.card)
                                .then(if (d == t && !isSel) Modifier.border(1.5.dp, C.accent, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) { BText(ld(d).dayOfMonth.toString(), 14, 500, if (isSel) C.onAccent else C.text) }
                    }
                }
            }
            // Chart panel
            BCard(Modifier.fillMaxWidth(), radius = 26, color = C.panel) {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        BText("This week", 17, 500)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(C.accent))
                            Spacer(Modifier.width(5.dp)); BText("Done", 11, 400, C.muted)
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.size(9.dp).clip(CircleShape).background(C.accent.copy(alpha = 0.35f)))
                            Spacer(Modifier.width(5.dp)); BText("Planned", 11, 400, C.muted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    WeekBars(week, total, done, sel) { sel = it }
                }
            }
            // Stats
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(Modifier.weight(1f), "$sumD", "Completed", Icons.Outlined.TaskAlt)
                StatCard(Modifier.weight(1f), "$rate%", "Completion", Icons.Outlined.PieChart)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(Modifier.weight(1f), "$streak", if (streak == 1) "Day streak" else "Day streak", Icons.Outlined.Whatshot)
                StatCard(Modifier.weight(1f), "$overdue", "Overdue now", Icons.Outlined.ErrorOutline)
            }
            // Selected day
            BCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    BText(fullDate(sel), 17, 500)
                    BText("$selDone of ${selList.size} completed", 13, 400, C.muted, Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(C.accent.copy(alpha = 0.15f))) {
                        val f = if (selList.isEmpty()) 0f else selDone.toFloat() / selList.size
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(50)).background(C.accent))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (selList.isEmpty()) {
                        BText("Nothing scheduled this day.", 13, 400, C.muted, Modifier.padding(vertical = 8.dp))
                    } else selList.take(8).forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(o.item) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (o.item.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                                tint = if (o.item.completed) C.accent else C.muted, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            BText(o.item.title, 14, 400, if (o.item.completed) C.muted else C.text, Modifier.weight(1f), maxLines = 1, strike = o.item.completed)
                            BText(if (o.item.allDay) "All day" else fmtTime(o.item.startMin), 12, 400, C.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBars(week: List<Long>, total: List<Int>, done: List<Int>, sel: Long, onSel: (Long) -> Unit) {
    val maxV = maxOf(1, total.maxOrNull() ?: 1)
    val accent = C.accent
    Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        week.forEachIndexed { i, d ->
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                    .background(if (d == sel) C.card.copy(alpha = 0.55f) else Color.Transparent)
                    .clickable { onSel(d) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BText(if (total[i] == 0) "" else "${done[i]}/${total[i]}", 10, 500, C.muted, align = TextAlign.Center, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.width(22.dp).weight(1f)) {
                    val h = size.height
                    val w = size.width
                    val r = 8.dp.toPx()
                    val totalH = if (total[i] == 0) 4.dp.toPx() else h * total[i] / maxV
                    val doneH = h * done[i] / maxV
                    val path = Path().apply {
                        addRoundRect(RoundRect(0f, h - totalH, w, h, CornerRadius(r, r)))
                    }
                    clipPath(path) {
                        drawRect(accent.copy(alpha = 0.18f), Offset(0f, h - totalH), Size(w, totalH))
                        val step = 8.dp.toPx()
                        var x = -totalH
                        while (x < w) {
                            drawLine(accent.copy(alpha = 0.45f), Offset(x, h), Offset(x + totalH, h - totalH), 2.dp.toPx())
                            x += step
                        }
                        if (doneH > 0f) drawRect(accent, Offset(0f, h - doneH), Size(w, doneH))
                    }
                }
                Spacer(Modifier.height(6.dp))
                BText(dowShort(d).take(1), 12, 500, if (d == sel) C.accent else C.muted)
            }
        }
    }
}
