package com.bipminder.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun Card(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    BCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) BText(title, 13, 600, C.muted)
            content()
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BText(label, 15, 500)
        Box(Modifier.clip(RoundedCornerShape(50)).background(C.accent.copy(alpha = 0.10f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
            BText(value, 14, 500, C.accent)
        }
    }
}

@Composable
fun EditorScreen(
    initial: Item, categories: List<String>, isNew: Boolean,
    onBack: () -> Unit, onSave: (Item) -> Unit, onDelete: (Item) -> Unit, onDuplicate: (Item) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember(initial.id) { mutableStateOf(initial) }
    var tagText by remember(initial.id) { mutableStateOf(initial.tagList().joinToString(", ")) }
    var dateDlg by remember { mutableIntStateOf(0) } // 1 start, 2 end
    var timeDlg by remember { mutableIntStateOf(0) } // 1 start, 2 end
    var addMenu by remember { mutableStateOf(false) }
    var customDlg by remember { mutableStateOf(false) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { Backup.importPhoto(ctx, uri) }
            if (n != null) d = d.copy(photo = n) else Toast.makeText(ctx, "Could not load photo", Toast.LENGTH_SHORT).show()
        }
    }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    var camFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) scope.launch {
            val n = withContext(Dispatchers.IO) { Backup.importPhoto(ctx, u).also { camFile?.delete() } }
            if (n != null) d = d.copy(photo = n)
        }
    }

    fun commit() {
        var x = d
        if (x.title.isBlank()) {
            x = x.copy(title = if (x.description.isNotBlank()) x.description.lines().first().take(60) else "Untitled")
        }
        if (x.type == Type.NOTE) x = x.copy(allDay = false, recurrence = Rec.NONE, reminders = "", completed = false)
        if (x.endDay < x.day) x = x.copy(endDay = x.day)
        if (x.endDay == x.day && !x.allDay && x.endMin < x.startMin) x = x.copy(endMin = x.startMin)
        x = x.copy(snoozeUntil = 0, tags = normalizeTags(tagText))
        onSave(x)
    }

    val isNote = d.type == Type.NOTE
    val offs = d.offsets()

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircleBtn(Icons.Outlined.ArrowBack, onBack, desc = "Back")
            BText(if (isNew) "New" else "Edit", 22, 300)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew) {
                    CircleBtn(Icons.Outlined.ContentCopy, { onDuplicate(d) }, desc = "Duplicate")
                    CircleBtn(Icons.Outlined.Delete, { onDelete(d) }, desc = "Delete")
                }
                CircleBtn(Icons.Outlined.Check, { commit() }, filled = true, desc = "Save")
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Task", "Event", "Note").forEachIndexed { i, n ->
                        Pill(n, d.type == i, { d = d.copy(type = i) }, onCard = true)
                    }
                }
                BField(d.title, { d = d.copy(title = it) }, "Title", size = 20, weight = 500)
                BField(d.description, { d = d.copy(description = it) }, "Description", singleLine = false, minLines = 3)
            }
            if (!isNote) {
                Card("SCHEDULE") {
                    FieldRow("Date", shortDate(d.day) + if (d.day == today()) " · Today" else "") { dateDlg = 1 }
                    FieldRow("Ends", shortDate(d.endDay)) { dateDlg = 2 }
                    SwitchRow("All day", d.allDay) { d = d.copy(allDay = it) }
                    if (!d.allDay) {
                        FieldRow("Start", fmtTime(d.startMin)) { timeDlg = 1 }
                        FieldRow("End", fmtTime(d.endMin)) { timeDlg = 2 }
                    }
                }
                Card("PRIORITY & REPEAT") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1, 2).forEach { p -> Pill(Pri.label(p), d.priority == p, { d = d.copy(priority = p) }, onCard = true) }
                    }
                    BText("Repeat", 13, 400, C.muted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Rec.NONE, Rec.DAILY, Rec.WEEKDAYS, Rec.WEEKLY, Rec.MONTHLY).forEach { r ->
                            Pill(recLabel(r), d.recurrence == r, { d = d.copy(recurrence = r) }, onCard = true)
                        }
                    }
                }
            }
            Card("ORGANIZE") {
                val cats = (listOf("Work", "Personal", "Health", "Study", "Home") + categories).distinct()
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.forEach { c ->
                        Pill(c, d.category.equals(c, true), { d = d.copy(category = if (d.category == c) "" else c) }, onCard = true)
                    }
                }
                BField(d.category, { d = d.copy(category = it) }, "Category")
                BField(tagText, { tagText = it }, "Tags (comma separated)", caps = false)
            }
            if (!isNote) {
                Card("REMINDERS") {
                    if (offs.isEmpty()) BText("No reminders", 14, 400, C.muted)
                    offs.forEach { m ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Notifications, null, tint = C.accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                BText(reminderLabel(m), 15, 500)
                            }
                            Box(Modifier.size(32.dp).clip(CircleShape).clickable {
                                d = d.copy(reminders = offs.filter { it != m }.joinToString(","))
                            }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Close, null, tint = C.muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (offs.size < Alarms.MAX_REMINDERS) {
                        Box {
                            Pill("+ Add reminder", false, { addMenu = true }, onCard = true)
                            DropdownMenu(addMenu, { addMenu = false }, modifier = Modifier.background(C.card)) {
                                listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880).forEach { m ->
                                    DropdownMenuItem(text = { BText(reminderLabel(m), 14) }, onClick = {
                                        addMenu = false
                                        d = d.copy(reminders = (offs + m).distinct().sorted().joinToString(","))
                                    })
                                }
                                DropdownMenuItem(text = { BText("Custom…", 14, 500, C.accent) }, onClick = { addMenu = false; customDlg = true })
                            }
                        }
                    }
                }
            }
            Card("PHOTO") {
                if (d.photo.isNotEmpty()) {
                    Box {
                        PhotoImage(d.photo, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)))
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(C.card)
                                .clickable { d = d.copy(photo = "") }, contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.Close, null, tint = C.text, modifier = Modifier.size(18.dp)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Camera", false, {
                        try {
                            val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                            val f = File(dir, "cap_${System.currentTimeMillis()}.jpg")
                            camFile = f
                            val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                            camUri = u
                            camera.launch(u)
                        } catch (e: Exception) { Toast.makeText(ctx, "No camera available", Toast.LENGTH_SHORT).show() }
                    }, onCard = true)
                    Pill("Gallery", false, {
                        try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        catch (e: Exception) { Toast.makeText(ctx, "No gallery available", Toast.LENGTH_SHORT).show() }
                    }, onCard = true)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (dateDlg != 0) {
        val which = dateDlg
        DatePickDialog(if (which == 1) d.day else d.endDay, { picked ->
            d = if (which == 1) d.copy(day = picked, endDay = picked + maxOf(0L, d.endDay - d.day))
            else d.copy(endDay = maxOf(picked, d.day))
        }, { dateDlg = 0 })
    }
    if (timeDlg != 0) {
        val which = timeDlg
        TimePickDialog(if (which == 1) d.startMin else d.endMin, { m ->
            d = if (which == 1) {
                val dur = (d.endMin - d.startMin).let { if (it <= 0) 60 else it }
                d.copy(startMin = m, endMin = minOf(1439, m + dur))
            } else d.copy(endMin = m)
        }, { timeDlg = 0 })
    }
    if (customDlg) CustomReminderDialog({ m ->
        d = d.copy(reminders = (offs + m).distinct().sorted().joinToString(","))
    }, { customDlg = false })
}

@Composable
private fun CustomReminderDialog(onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    var num by remember { mutableStateOf("15") }
    var unit by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.card,
        title = { BText("Custom reminder", 18, 500) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BField(num, { v -> num = v.filter { it.isDigit() }.take(4) }, "Amount", caps = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Minutes", "Hours", "Days").forEachIndexed { i, n -> Pill(n, unit == i, { unit = i }, onCard = true) }
                }
                BText("before the start time", 13, 400, C.muted)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = num.toIntOrNull() ?: 0
                onAdd(n * when (unit) { 1 -> 60; 2 -> 1440; else -> 1 })
                onDismiss()
            }) { BText("Add", 14, 600, C.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { BText("Cancel", 14, 500, C.muted) } }
    )
}
