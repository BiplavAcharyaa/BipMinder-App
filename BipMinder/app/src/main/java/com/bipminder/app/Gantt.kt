package com.bipminder.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val palette = listOf(
    Color(0xFF6269C5), Color(0xFF3FA7A0), Color(0xFFE07A5F),
    Color(0xFFB06AC4), Color(0xFF4F8FD6), Color(0xFFC9923A)
)

fun ganttColor(i: Item): Color {
    if (i.completed) return Color(0xFF8E8CA3)
    val idx = if (i.category.isBlank()) i.type * 2 + i.priority else abs(i.category.hashCode())
    return palette[idx % palette.size]
}

@Composable
fun GanttScreen(all: List<Item>, s: AppSettings, onOpen: (Item) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(1) }
    var anchor by rememberSaveable { mutableStateOf(today()) }

    val from: Long
    val days: Int
    when (mode) {
        0 -> { from = anchor; days = 1 }
        1 -> { from = weekStart(anchor, s.weekMonday); days = 7 }
        else -> { val f = ld(anchor).withDayOfMonth(1); from = f.toEpochDay(); days = f.lengthOfMonth() }
    }
    val unitMin = if (mode == 0) 60 else 1440
    val unitCount = if (mode == 0) 24 else days
    val unitDp = when (mode) { 0 -> 52f; 1 -> 88f; else -> 40f }
    val dpPerMin = unitDp / unitMin
    val rows = remember(all, from, days) { occurrences(all, from, from + days - 1) }
    val t = today()

    fun move(dir: Int) {
        anchor = when (mode) {
            0 -> anchor + dir
            1 -> anchor + 7L * dir
            else -> ld(anchor).plusMonths(dir.toLong()).toEpochDay()
        }
    }
    val title = when (mode) {
        0 -> dayLabel(anchor)
        1 -> "${shortDate(from)} – ${shortDate(from + 6)}"
        else -> monthTitle(anchor)
    }
    val labelW = 108.dp
    val rowH = 44.dp
    val headH = 44.dp
    val lineColor = C.line
    val nowColor = Pri.high
    val accent = C.accent

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
        ScreenTitle("Gantt") { CircleBtn(Icons.Outlined.Today, { anchor = t }, desc = "Today") }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Daily", "Weekly", "Monthly").forEachIndexed { i, n -> Pill(n, mode == i, { mode = i }) }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircleBtn(Icons.Outlined.ChevronLeft, { move(-1) }, size = 38)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BText(title, 17, 500)
                BText("${rows.size} scheduled", 12, 400, C.muted)
            }
            CircleBtn(Icons.Outlined.ChevronRight, { move(1) }, size = 38)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            BCard(Modifier.fillMaxWidth()) {
                if (rows.isEmpty()) {
                    EmptyState(Icons.Outlined.Timeline, "Timeline is empty", "Nothing is scheduled in this period.")
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.width(labelW)) {
                            Spacer(Modifier.height(headH))
                            rows.forEach { occ ->
                                Box(Modifier.height(rowH).fillMaxWidth().padding(start = 14.dp, end = 6.dp), contentAlignment = Alignment.CenterStart) {
                                    BText(occ.item.title, 12, 500, maxLines = 1)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                            Column(
                                Modifier.width((unitDp * unitCount).dp).drawBehind {
                                    val u = unitDp.dp.toPx()
                                    for (i in 1 until unitCount) {
                                        drawLine(lineColor, Offset(i * u, headH.toPx()), Offset(i * u, size.height), 1f)
                                    }
                                    if (t in from until from + days) {
                                        val mins = (System.currentTimeMillis() - millisAt(from, 0)) / 60000f
                                        val x = mins * dpPerMin.dp.toPx()
                                        if (x in 0f..size.width) drawLine(nowColor, Offset(x, 0f), Offset(x, size.height), 2f)
                                    }
                                }
                            ) {
                                Row(Modifier.height(headH)) {
                                    for (i in 0 until unitCount) {
                                        Box(Modifier.width(unitDp.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                            when (mode) {
                                                0 -> BText(fmtHour(i), 11, 500, C.muted, align = TextAlign.Center)
                                                1 -> {
                                                    val d = from + i
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        BText(dowShort(d), 11, 400, C.muted)
                                                        BText(ld(d).dayOfMonth.toString(), 14, 600, if (d == t) accent else C.text)
                                                    }
                                                }
                                                else -> {
                                                    val d = from + i
                                                    BText(ld(d).dayOfMonth.toString(), 11, if (d == t) 700 else 500, if (d == t) accent else C.muted)
                                                }
                                            }
                                        }
                                    }
                                }
                                rows.forEach { occ ->
                                    val sM = ((occ.day - from) * 1440 + occ.startMin).coerceAtLeast(0)
                                    val eM = ((occ.endDay - from) * 1440 + occ.endMin).coerceAtMost(days * 1440L)
                                    val x = sM * dpPerMin
                                    val w = maxOf((eM - sM) * dpPerMin, 30f)
                                    Box(Modifier.height(rowH).fillMaxWidth()) {
                                        Box(
                                            Modifier.offset(x = x.dp, y = 8.dp).width(w.dp).height(28.dp)
                                                .clip(RoundedCornerShape(10.dp)).background(ganttColor(occ.item))
                                                .clickable { onOpen(occ.item) }.padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) { BText(occ.item.title, 11, 500, Color.White, maxLines = 1) }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
