package com.bipminder.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BText(
    text: String, size: Int = 14, weight: Int = 400, color: Color = C.text, modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE, strike: Boolean = false, align: TextAlign? = null
) {
    Text(
        text = text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = FontWeight(weight),
        fontFamily = LocalInter.current, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        textDecoration = if (strike) TextDecoration.LineThrough else null, textAlign = align,
        lineHeight = (size * (if (size >= 28) 1.15f else 1.35f)).sp
    )
}

@Composable
fun BCard(
    modifier: Modifier = Modifier, radius: Int = 22, onClick: (() -> Unit)? = null,
    color: Color = C.card, content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius.dp)
    var m = modifier
        .shadow(3.dp, shape, clip = false, ambientColor = Color(0x14000000), spotColor = Color(0x26000000))
        .clip(shape).background(color)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, content = content)
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onCard: Boolean = false) {
    val bg = when {
        selected -> C.accent
        onCard -> C.accent.copy(alpha = 0.10f)
        else -> C.chip
    }
    Box(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { BText(label, 13, 500, if (selected) C.onAccent else C.text, maxLines = 1) }
}

@Composable
fun CircleBtn(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: Int = 44, filled: Boolean = false, desc: String? = null) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(if (filled) C.accent else C.card).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = if (filled) C.onAccent else C.text, modifier = Modifier.size((size * 0.46f).dp)) }
}

@Composable
fun ScreenTitle(title: String, hpad: Int = 20, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = hpad.dp, end = hpad.dp, top = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        BText(title, 34, 300)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(C.card.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.accent, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        BText(title, 17, 500, align = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        BText(sub, 13, 400, C.muted, align = TextAlign.Center)
    }
}

@Composable
fun CheckCircle(done: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ring = if (enabled) C.accent else C.muted.copy(alpha = 0.5f)
    Box(
        Modifier.size(28.dp).clip(CircleShape)
            .background(if (done) C.accent else Color.Transparent)
            .border(1.5.dp, if (done) C.accent else ring, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (done) Icon(Icons.Outlined.Check, null, tint = C.onAccent, modifier = Modifier.size(16.dp))
        else if (!enabled) Icon(Icons.Outlined.Repeat, null, tint = C.muted, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun PhotoImage(name: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            try {
                val f = File(Backup.photosDir(ctx), name)
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, b)
                var s = 1
                while (maxOf(b.outWidth, b.outHeight) / s > 1000) s *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            } catch (e: Exception) { null }
        }
    }
    val b = bmp
    if (b != null) Image(b, null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(C.accent.copy(alpha = 0.08f)))
}

@Composable
fun BField(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    singleLine: Boolean = true, minLines: Int = 1, size: Int = 15, weight: Int = 400,
    focus: FocusRequester? = null, caps: Boolean = true
) {
    val m = if (focus != null) modifier.fillMaxWidth().focusRequester(focus) else modifier.fillMaxWidth()
    BasicTextField(
        value = value, onValueChange = onChange, modifier = m, singleLine = singleLine, minLines = minLines,
        textStyle = TextStyle(color = C.text, fontSize = size.sp, fontWeight = FontWeight(weight), fontFamily = LocalInter.current),
        cursorBrush = SolidColor(C.accent),
        keyboardOptions = KeyboardOptions(capitalization = if (caps) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.accent.copy(alpha = 0.08f)).padding(14.dp)) {
                if (value.isEmpty()) BText(hint, size, 400, C.muted)
                inner()
            }
        }
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        BText(label, 15, 500)
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = C.accent, checkedThumbColor = Color.White,
                uncheckedTrackColor = C.accent.copy(alpha = 0.15f), uncheckedThumbColor = C.muted,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(50)).background(C.card)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, null, tint = C.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) BText("Search title, notes, tags", 15, 400, C.muted)
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true,
                textStyle = TextStyle(color = C.text, fontSize = 15.sp, fontFamily = LocalInter.current),
                cursorBrush = SolidColor(C.accent)
            )
        }
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, null, tint = C.text, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemCard(
    item: Item, occDay: Long, showDate: Boolean, canToggle: Boolean,
    onOpen: () -> Unit, onToggle: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val overdue = item.isOverdue()
    var menu by remember { mutableStateOf(false) }
    BCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)) {
            if (item.type == Type.NOTE) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(C.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Description, null, tint = C.accent, modifier = Modifier.size(15.dp))
                }
            } else CheckCircle(item.completed, canToggle, onToggle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BText(item.title, 16, 500, if (item.completed) C.muted else C.text, maxLines = 2, strike = item.completed)
                if (item.description.isNotBlank()) {
                    BText(item.description, 13, 400, C.muted, Modifier.padding(top = 2.dp), maxLines = 2)
                }
                FlowRow(
                    Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.type != Type.NOTE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AccessTime, null, tint = C.muted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            BText((if (showDate) dayLabel(occDay) + " · " else "") + timeLabel(item), 12, 400, C.muted)
                        }
                    } else {
                        BText("Note", 12, 400, C.muted)
                    }
                    if (overdue) BText("Overdue", 12, 500, Pri.high)
                    if (item.recurrence != Rec.NONE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Repeat, null, tint = C.muted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            BText(recLabel(item.recurrence), 12, 400, C.muted)
                        }
                    }
                    if (item.category.isNotBlank()) BText(item.category, 12, 500, C.accent)
                    item.tagList().take(3).forEach { BText("#$it", 12, 400, C.muted) }
                }
                if (item.photo.isNotEmpty()) {
                    PhotoImage(item.photo, Modifier.padding(top = 10.dp).fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box {
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.MoreVert, null, tint = C.muted, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(menu, { menu = false }, modifier = Modifier.background(C.card)) {
                        DropdownMenuItem(text = { BText("Edit", 14) }, onClick = { menu = false; onOpen() },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = C.text) })
                        DropdownMenuItem(text = { BText("Duplicate", 14) }, onClick = { menu = false; onDuplicate() },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, tint = C.text) })
                        DropdownMenuItem(text = { BText("Delete", 14, 400, Pri.high) }, onClick = { menu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Pri.high) })
                    }
                }
                if (item.type != Type.NOTE) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50))
                            .background(Pri.color(item.priority).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) { BText(Pri.label(item.priority), 11, 500, Pri.color(item.priority)) }
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, value: String, label: String, icon: ImageVector) {
    BCard(modifier, radius = 18) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = C.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
            BText(value, 22, 500)
            BText(label, 11, 400, C.muted, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(day: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onPick(Math.floorDiv(it, 86_400_000L)) }
                onDismiss()
            }) { BText("OK", 14, 600, C.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { BText("Cancel", 14, 500, C.muted) } },
        colors = DatePickerDefaults.colors(containerColor = C.card)
    ) {
        DatePicker(
            state = st,
            colors = DatePickerDefaults.colors(
                containerColor = C.card, selectedDayContainerColor = C.accent,
                todayContentColor = C.accent, todayDateBorderColor = C.accent,
                selectedYearContainerColor = C.accent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(min: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val st = rememberTimePickerState(initialHour = min / 60, initialMinute = min % 60, is24Hour = Fmt.h24)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = C.card, modifier = Modifier.width(IntrinsicSize.Min)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(
                    state = st,
                    colors = TimePickerDefaults.colors(
                        selectorColor = C.accent,
                        periodSelectorSelectedContainerColor = C.accent.copy(alpha = 0.2f),
                        timeSelectorSelectedContainerColor = C.accent.copy(alpha = 0.2f),
                        clockDialSelectedContentColor = Color.White
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { BText("Cancel", 14, 500, C.muted) }
                    TextButton(onClick = { onPick(st.hour * 60 + st.minute); onDismiss() }) { BText("OK", 14, 600, C.accent) }
                }
            }
        }
    }
}
