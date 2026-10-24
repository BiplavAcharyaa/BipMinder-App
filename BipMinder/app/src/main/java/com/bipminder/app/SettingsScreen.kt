package com.bipminder.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        BText(title, 13, 600, C.muted, Modifier.padding(start = 6.dp, bottom = 8.dp))
        BCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = C.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BText(title, 15, 500)
            BText(sub, 12, 400, C.muted)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = C.muted)
    }
}

private fun launchSafe(ctx: Context, i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }
}

@Composable
fun SettingsScreen(
    s: AppSettings, tick: Int, onChange: ((AppSettings) -> AppSettings) -> Unit,
    onRequestNotif: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, itemCount: Int
) {
    val ctx = LocalContext.current
    val canPost = remember(tick) { Notifier.canPost(ctx) }
    val exact = remember(tick) { Alarms.canExact(ctx) }
    val battery = remember(tick) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    var name by remember(s.name) { mutableStateOf(s.name) }
    var confirmImport by remember { mutableStateOf(false) }
    var timeDlg by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
        ScreenTitle("Settings")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Section("PROFILE") {
                BText("Your name (used in the greeting)", 13, 400, C.muted)
                BField(name, { name = it; onChange { st -> st.copy(name = it) } }, "Name")
            }
            Section("APPEARANCE") {
                BText("Theme", 13, 400, C.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Light", "Dark").forEachIndexed { i, n ->
                        Pill(n, s.theme == i, { onChange { it.copy(theme = i) } }, onCard = true)
                    }
                }
                SwitchRow("Week starts on Monday", s.weekMonday) { v -> onChange { it.copy(weekMonday = v) } }
            }
            Section("NOTIFICATIONS") {
                SwitchRow("Enable reminders", s.notifications) { v -> onChange { it.copy(notifications = v) } }
                BText("Default reminder for new items", 13, 400, C.muted)
                val opts = listOf(-1 to "None", 0 to "At time", 10 to "10 min", 30 to "30 min", 60 to "1 hour", 1440 to "1 day")
                Row(Modifier.horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    opts.forEach { (v, n) -> Pill(n, s.defaultReminder == v, { onChange { it.copy(defaultReminder = v) } }, onCard = true) }
                }
                BText("Snooze length", 13, 400, C.muted)
                Row(Modifier.horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 60).forEach { v ->
                        Pill("$v min", s.snooze == v, { onChange { it.copy(snooze = v) } }, onCard = true)
                    }
                }
                ActionRow(Icons.Outlined.Alarm, "All-day reminder time", fmtTime(s.allDayMin)) { timeDlg = true }
                ActionRow(Icons.Outlined.Notifications, "Send test notification", "Check that alerts appear") {
                    if (!canPost) onRequestNotif()
                    Notifier.test(ctx)
                }
            }
            Section("RELIABILITY") {
                StatusLine("Notification permission", canPost, "Allowed", "Blocked")
                if (!canPost) {
                    ActionRow(Icons.Outlined.Notifications, "Allow notifications", "Required to show reminders") {
                        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) onRequestNotif()
                        else launchSafe(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }
                }
                StatusLine("Exact alarms", exact, "Allowed", "Not allowed")
                if (!exact && Build.VERSION.SDK_INT >= 31) {
                    ActionRow(Icons.Outlined.Alarm, "Allow exact alarms", "Without it reminders may arrive late") {
                        launchSafe(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                    }
                }
                StatusLine("Battery optimization", battery, "Unrestricted", "Optimized")
                if (!battery) {
                    ActionRow(Icons.Outlined.Warning, "Disable battery optimization", "Keeps reminders reliable in the background") {
                        launchSafe(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                    }
                }
                ActionRow(Icons.Outlined.Info, "App settings", "Autostart, battery and notification options") {
                    launchSafe(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                }
            }
            Section("DATA") {
                BText("$itemCount items stored on this device", 13, 400, C.muted)
                ActionRow(Icons.Outlined.FileUpload, "Export backup", "Save items and photos to a .zip file") { onExport() }
                ActionRow(Icons.Outlined.FileDownload, "Restore backup", "Replace current data from a .zip backup") { confirmImport = true }
            }
            Section("ABOUT") {
                BText("BipMinder works fully offline. All reminders, notes and photos stay on this device. No account, no cloud.", 13, 400, C.muted)
            }
        }
    }

    if (timeDlg) TimePickDialog(s.allDayMin, { m -> onChange { it.copy(allDayMin = m) } }, { timeDlg = false })
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false }, containerColor = C.card,
            title = { BText("Restore backup?", 18, 500) },
            text = { BText("This replaces all current items with the contents of the backup.", 14, 400, C.muted) },
            confirmButton = { TextButton(onClick = { confirmImport = false; onImport() }) { BText("Choose file", 14, 600, C.accent) } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { BText("Cancel", 14, 500, C.muted) } }
        )
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean, good: String, bad: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        BText(label, 15, 500)
        BText(if (ok) good else bad, 13, 600, if (ok) Pri.low else Pri.med)
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))
