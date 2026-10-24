<#
  BipMinder - one-shot generator and builder for Windows PowerShell 5.1+
  Creates the full Android project, installs JDK 17 + Android SDK + Gradle if missing,
  and builds the debug APK with .\gradlew.bat assembleDebug. No Android Studio needed.
  Run:  powershell -ExecutionPolicy Bypass -File .\BipMinder-Setup.ps1
#>
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root  = Join-Path $HOME 'BipMinder'
$Tools = Join-Path $HOME 'BipMinderTools'
$Sdk   = Join-Path $Tools 'android-sdk'
New-Item -ItemType Directory -Force -Path $Root, $Tools, $Sdk | Out-Null

function W([string]$rel, [string]$text) {
    $p = Join-Path $Root $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $p) | Out-Null
    $t = $text -replace "`r?`n", "`n"
    [IO.File]::WriteAllText($p, $t, (New-Object Text.UTF8Encoding $false))
}

Write-Host '==> Writing project files' -ForegroundColor Cyan
W 'app\build.gradle.kts' @'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.bipminder.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bipminder.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
'@

W 'app\src\main\AndroidManifest.xml' @'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <queries>
        <intent>
            <action android:name="android.media.action.IMAGE_CAPTURE" />
        </intent>
    </queries>

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.BipMinder">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:windowSoftInputMode="adjustResize"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize|uiMode|density|locale|layoutDirection">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver android:name=".AlarmReceiver" android:exported="false" />
        <receiver android:name=".ActionReceiver" android:exported="false" />
        <receiver android:name=".BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
                <action android:name="android.intent.action.DATE_CHANGED" />
                <action android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" />
            </intent-filter>
        </receiver>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
'@

W 'app\src\main\java\com\bipminder\app\Alarms.kt' @'
package com.bipminder.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Alarms {
    const val ACTION_ALARM = "com.bipminder.app.ALARM"
    const val MAX_REMINDERS = 6
    const val SNOOZE_IDX = 15

    fun code(id: Long, idx: Int): Int = id.toInt() * 16 + idx

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun pending(ctx: Context, id: Long, idx: Int, trig: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_ALARM)
            .putExtra("id", id).putExtra("idx", idx).putExtra("trig", trig)
        return PendingIntent.getBroadcast(ctx, code(id, idx), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancelItem(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (idx in (0 until MAX_REMINDERS) + SNOOZE_IDX) {
            val p = pending(ctx, id, idx, 0)
            am.cancel(p); p.cancel()
        }
    }

    private fun set(ctx: Context, id: Long, idx: Int, t: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pending(ctx, id, idx, t)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
        }
    }

    fun scheduleItem(ctx: Context, item: Item, s: AppSettings, after: Long = System.currentTimeMillis()) {
        cancelItem(ctx, item.id)
        if (!s.notifications || item.type == Type.NOTE || item.completed) return
        item.offsets().forEachIndexed { idx, off ->
            item.nextTrigger(off, after, s.allDayMin)?.let { set(ctx, item.id, idx, it) }
        }
        if (item.snoozeUntil > System.currentTimeMillis()) set(ctx, item.id, SNOOZE_IDX, item.snoozeUntil)
    }

    /** Re-arm a single reminder slot after it fired (next occurrence for recurring items). */
    fun scheduleOne(ctx: Context, item: Item, idx: Int, firedAt: Long, s: AppSettings) {
        if (idx >= MAX_REMINDERS || !s.notifications || item.completed || item.type == Type.NOTE) return
        val off = item.offsets().getOrNull(idx) ?: return
        item.nextTrigger(off, firedAt, s.allDayMin)?.let { set(ctx, item.id, idx, it) }
    }

    suspend fun rescheduleAll(ctx: Context) {
        val c = ctx.applicationContext
        val s = c.settingsFlow().first()
        AppDb.get(c).dao().allNow().forEach { scheduleItem(c, it, s) }
    }
}

object Notifier {
    const val CHANNEL = "bip_reminders"
    const val ACTION_COMPLETE = "com.bipminder.app.COMPLETE"
    const val ACTION_SNOOZE = "com.bipminder.app.SNOOZE"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Task, event and schedule reminders"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun canPost(ctx: Context): Boolean {
        val perm = Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return perm && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun actionPi(ctx: Context, action: String, id: Long): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(action).putExtra("id", id)
        return PendingIntent.getBroadcast(ctx, id.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, item: Item, s: AppSettings) {
        ensureChannel(ctx)
        if (!canPost(ctx)) return
        Fmt.h24 = DateFormat.is24HourFormat(ctx)
        val open = PendingIntent.getActivity(ctx, item.id.toInt(),
            Intent(ctx, MainActivity::class.java).putExtra("openId", item.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val whenText = if (item.allDay) "All day" else "${fmtTime(item.startMin)} – ${fmtTime(item.endMin)}"
        val body = if (item.description.isBlank()) whenText else "$whenText · ${item.description}"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(item.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Complete", actionPi(ctx, ACTION_COMPLETE, item.id))
            .addAction(0, "Snooze ${s.snooze}m", actionPi(ctx, ACTION_SNOOZE, item.id))
            .build()
        NotificationManagerCompat.from(ctx).notify(item.id.toInt(), n)
    }

    @SuppressLint("MissingPermission")
    fun test(ctx: Context) {
        ensureChannel(ctx)
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("BipMinder test")
            .setContentText("Notifications are working.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(999_999, n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val id = intent.getLongExtra("id", -1)
        val idx = intent.getIntExtra("idx", 0)
        val trig = intent.getLongExtra("trig", 0)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = ctx.applicationContext
                val dao = AppDb.get(c).dao()
                val s = c.settingsFlow().first()
                var item = dao.get(id)
                if (item != null && !item.completed && s.notifications) {
                    Notifier.show(c, item, s)
                    if (idx == Alarms.SNOOZE_IDX) {
                        item = item.copy(snoozeUntil = 0); dao.update(item)
                    }
                    Alarms.scheduleOne(c, item, idx, maxOf(trig, System.currentTimeMillis() - 1000), s)
                }
            } finally { pending.finish() }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val id = intent.getLongExtra("id", -1)
        val action = intent.action
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = ctx.applicationContext
                val s = c.settingsFlow().first()
                when (action) {
                    Notifier.ACTION_COMPLETE -> Repo.complete(c, id)
                    Notifier.ACTION_SNOOZE -> Repo.snooze(c, id, s.snooze)
                }
                NotificationManagerCompat.from(c).cancel(id.toInt())
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Alarms.rescheduleAll(ctx) } finally { pending.finish() }
        }
    }
}
'@

W 'app\src\main\java\com\bipminder\app\Backup.kt' @'
package com.bipminder.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Backup {
    fun photosDir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun importPhoto(ctx: Context, uri: Uri): String? {
        return try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp: Bitmap = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val deg = try { cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0 } catch (e: Exception) { 0 }
            if (deg != 0) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height,
                    Matrix().apply { postRotate(deg.toFloat()) }, true)
            }
            val name = "img_${System.currentTimeMillis()}.jpg"
            FileOutputStream(File(photosDir(ctx), name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            name
        } catch (e: Exception) { null }
    }

    fun cleanOrphans(ctx: Context, used: Set<String>) {
        photosDir(ctx).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    private fun toJson(i: Item) = JSONObject()
        .put("id", i.id).put("type", i.type).put("title", i.title).put("description", i.description)
        .put("day", i.day).put("endDay", i.endDay).put("startMin", i.startMin).put("endMin", i.endMin)
        .put("allDay", i.allDay).put("priority", i.priority).put("category", i.category).put("tags", i.tags)
        .put("recurrence", i.recurrence).put("reminders", i.reminders).put("completed", i.completed)
        .put("completedAt", i.completedAt).put("photo", i.photo).put("createdAt", i.createdAt)

    private fun fromJson(o: JSONObject): Item {
        val day = o.optLong("day", today())
        return Item(
            id = o.optLong("id", 0), type = o.optInt("type", 0), title = o.optString("title", ""),
            description = o.optString("description", ""), day = day, endDay = o.optLong("endDay", day),
            startMin = o.optInt("startMin", 540), endMin = o.optInt("endMin", 600),
            allDay = o.optBoolean("allDay", false), priority = o.optInt("priority", 1),
            category = o.optString("category", ""), tags = o.optString("tags", ""),
            recurrence = o.optInt("recurrence", 0), reminders = o.optString("reminders", ""),
            completed = o.optBoolean("completed", false), completedAt = o.optLong("completedAt", 0),
            photo = o.optString("photo", ""), createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            snoozeUntil = 0
        )
    }

    suspend fun export(ctx: Context, uri: Uri): Int {
        val c = ctx.applicationContext
        val items = AppDb.get(c).dao().allNow()
        val arr = JSONArray(); items.forEach { arr.put(toJson(it)) }
        val root = JSONObject().put("app", "BipMinder").put("version", 1)
            .put("exportedAt", System.currentTimeMillis()).put("items", arr)
        val out = c.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Cannot write file")
        out.use { os ->
            ZipOutputStream(os).use { z ->
                z.putNextEntry(ZipEntry("data.json"))
                z.write(root.toString(2).toByteArray(Charsets.UTF_8))
                z.closeEntry()
                items.map { it.photo }.filter { it.isNotEmpty() }.distinct().forEach { name ->
                    val f = File(photosDir(c), name)
                    if (f.exists()) {
                        z.putNextEntry(ZipEntry("photos/$name"))
                        f.inputStream().use { it.copyTo(z) }
                        z.closeEntry()
                    }
                }
            }
        }
        return items.size
    }

    suspend fun import(ctx: Context, uri: Uri): Int {
        val c = ctx.applicationContext
        var json: String? = null
        val input = c.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot read file")
        input.use { ins ->
            ZipInputStream(ins).use { z ->
                var e: ZipEntry? = z.nextEntry
                while (e != null) {
                    val n = e.name
                    if (n == "data.json") json = z.readBytes().toString(Charsets.UTF_8)
                    else if (n.startsWith("photos/") && !e.isDirectory) {
                        val safe = File(n).name
                        if (safe.isNotEmpty()) FileOutputStream(File(photosDir(c), safe)).use { z.copyTo(it) }
                    }
                    z.closeEntry()
                    e = z.nextEntry
                }
            }
        }
        val text = json ?: throw IllegalStateException("Not a BipMinder backup")
        val arr = JSONObject(text).getJSONArray("items")
        val items = (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        val dao = AppDb.get(c).dao()
        dao.allNow().forEach { Alarms.cancelItem(c, it.id) }
        dao.clear()
        dao.insertAll(items)
        Alarms.rescheduleAll(c)
        return items.size
    }
}
'@

W 'app\src\main\java\com\bipminder\app\CalendarScreen.kt' @'
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
'@

W 'app\src\main\java\com\bipminder\app\Components.kt' @'
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
'@

W 'app\src\main\java\com\bipminder\app\Data.kt' @'
package com.bipminder.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

object Type { const val TASK = 0; const val EVENT = 1; const val NOTE = 2 }
object Rec { const val NONE = 0; const val DAILY = 1; const val WEEKLY = 2; const val MONTHLY = 3; const val WEEKDAYS = 4 }

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int = Type.TASK,
    val title: String = "",
    val description: String = "",
    val day: Long = LocalDate.now().toEpochDay(),
    val endDay: Long = day,
    val startMin: Int = 540,
    val endMin: Int = 600,
    val allDay: Boolean = false,
    val priority: Int = 1,
    val category: String = "",
    val tags: String = "",
    val recurrence: Int = Rec.NONE,
    val reminders: String = "0",
    val completed: Boolean = false,
    val completedAt: Long = 0,
    val photo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val snoozeUntil: Long = 0
)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items") fun all(): Flow<List<Item>>
    @Query("SELECT * FROM items") suspend fun allNow(): List<Item>
    @Query("SELECT * FROM items WHERE id = :id") suspend fun get(id: Long): Item?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(i: Item): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(l: List<Item>)
    @Update suspend fun update(i: Item)
    @Delete suspend fun delete(i: Item)
    @Query("DELETE FROM items") suspend fun clear()
}

@Database(entities = [Item::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): ItemDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "bipminder.db")
                .build().also { inst = it }
        }
    }
}
'@

W 'app\src\main\java\com\bipminder\app\Editor.kt' @'
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
'@

W 'app\src\main\java\com\bipminder\app\Gantt.kt' @'
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
'@

W 'app\src\main\java\com\bipminder\app\Home.kt' @'
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
'@

W 'app\src\main\java\com\bipminder\app\MainActivity.kt' @'
package com.bipminder.app

import android.Manifest
import android.net.Uri
import android.os.Build
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)
    private val openId = mutableLongStateOf(-1L)
    private val vm: MainViewModel by viewModels()

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick.intValue++ }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri)
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    private fun applyBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun readIntent(i: Intent?) {
        val id = i?.getLongExtra("openId", -1L) ?: -1L
        if (id > 0) openId.longValue = id
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        Fmt.h24 = DateFormat.is24HourFormat(this)
        readIntent(intent)
        setContent {
            val s by vm.settings.collectAsState()
            val dark = when (s.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            SideEffect { applyBars(dark) }
            BipTheme(dark) {
                BipApp(
                    vm, tick.intValue, openId.longValue, { openId.longValue = -1L },
                    { if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    { exportLauncher.launch("BipMinder-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip") },
                    { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Fmt.h24 = DateFormat.is24HourFormat(this)
        tick.intValue++
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home),
    Tab("tasks", "Tasks", Icons.Outlined.TaskAlt),
    Tab("calendar", "Calendar", Icons.Outlined.CalendarMonth),
    Tab("gantt", "Gantt", Icons.Outlined.Timeline),
    Tab("progress", "Progress", Icons.Outlined.BarChart),
    Tab("settings", "Settings", Icons.Outlined.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BipApp(
    vm: MainViewModel, tick: Int, openId: Long, onOpened: () -> Unit,
    requestNotif: () -> Unit, exportBackup: () -> Unit, importBackup: () -> Unit
) {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val all by vm.items.collectAsState()
    val s by vm.settings.collectAsState()
    val snack = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = tabs.any { it.route == route }
    var quick by remember { mutableStateOf(false) }
    var calSel by rememberSaveable { mutableStateOf(today()) }

    LaunchedEffect(Unit) {
        vm.undo.collect { item ->
            val r = snack.showSnackbar("Deleted \"${item.title}\"", "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.restore(item)
        }
    }
    LaunchedEffect(Unit) { vm.toast.collect { snack.showSnackbar(it, duration = SnackbarDuration.Short) } }
    LaunchedEffect(openId) {
        if (openId > 0) { nav.navigate("edit/$openId"); onOpened() }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) requestNotif()
    }

    val warn = remember(tick, s.notifications) {
        when {
            !s.notifications -> null
            !Notifier.canPost(ctx) -> "Notifications are blocked, so reminders cannot appear."
            !Alarms.canExact(ctx) -> "Exact alarms are off, so reminders may arrive late."
            else -> null
        }
    }

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val open: (Item) -> Unit = { nav.navigate("edit/${it.id}") }
    fun leaveEditor() {
        if (nav.currentDestination?.route == "edit/{id}") nav.popBackStack()
    }

    Scaffold(
        containerColor = C.bg,
        snackbarHost = {
            SnackbarHost(snack) { data ->
                Snackbar(data, containerColor = C.text, contentColor = C.bg, actionColor = if (C.dark) C.accent else Color(0xFFB8BDF2))
            }
        },
        bottomBar = {
            if (isTab) {
                NavigationBar(containerColor = C.card, tonalElevation = 0.dp, modifier = Modifier.shadow(10.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route, onClick = { goTab(t.route) },
                            icon = { Icon(t.icon, t.label, modifier = Modifier.size(24.dp)) },
                            label = { BText(t.label, 10, 500, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = C.accent, selectedTextColor = C.accent,
                                unselectedIconColor = C.muted, unselectedTextColor = C.muted,
                                indicatorColor = C.accent.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route in listOf("home", "tasks", "calendar", "gantt")) {
                FloatingActionButton(
                    onClick = { quick = true }, shape = CircleShape, containerColor = C.accent, contentColor = C.onAccent,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) { Icon(Icons.Outlined.Add, "Add", modifier = Modifier.size(28.dp)) }
            }
        }
    ) { pad ->
        NavHost(
            nav, startDestination = "home", modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) }, popExitTransition = { fadeOut(tween(160)) }
        ) {
            composable("home") {
                HomeScreen(all, s, warn, { goTab("settings") }, open, vm::toggle, vm::duplicate, { vm.delete(it) })
            }
            composable("tasks") { TasksScreen(all, open, vm::toggle, vm::duplicate, { vm.delete(it) }) }
            composable("calendar") {
                CalendarScreen(all, s, calSel, { calSel = it }, open, vm::toggle, vm::duplicate, { vm.delete(it) })
            }
            composable("gantt") { GanttScreen(all, s, open) }
            composable("progress") { ProgressScreen(all, s, open) }
            composable("settings") {
                SettingsScreen(s, tick, { f -> vm.updateSettings(f) }, requestNotif, exportBackup, importBackup, all.size)
            }
            composable(
                "edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(180)) }
            ) { be ->
                val id = be.arguments?.getLong("id") ?: 0L
                val existing = all.firstOrNull { it.id == id }
                if (id != 0L && existing == null) {
                    // item was deleted or not loaded yet
                    LaunchedEffect(id) { kotlinx.coroutines.delay(1500); leaveEditor() }
                } else {
                    val seed = remember(id) {
                        existing ?: vm.seed ?: Item(day = today(), reminders = defaultReminders(s))
                    }
                    EditorScreen(
                        initial = seed,
                        categories = all.map { it.category }.filter { it.isNotBlank() }.distinct(),
                        isNew = id == 0L,
                        onBack = { vm.seed = null; leaveEditor() },
                        onSave = { item -> vm.seed = null; vm.save(item) { leaveEditor() } },
                        onDelete = { item -> vm.seed = null; vm.delete(existing ?: item) { leaveEditor() } },
                        onDuplicate = { item -> vm.duplicate(item); leaveEditor() }
                    )
                }
            }
        }
    }

    if (quick) {
        QuickAddSheet(
            baseDay = if (route == "calendar") calSel else today(), s = s,
            onDismiss = { quick = false },
            onAdd = { item -> quick = false; vm.save(item); },
            onDetails = { item -> quick = false; vm.seed = item; nav.navigate("edit/0") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(baseDay: Long, s: AppSettings, onDismiss: () -> Unit, onAdd: (Item) -> Unit, onDetails: (Item) -> Unit) {
    var text by remember { mutableStateOf("") }
    var type by remember { mutableIntStateOf(Type.TASK) }
    val fr = remember { FocusRequester() }
    val parsed = remember(text, type) { parseQuick(text, type, baseDay, s) }
    LaunchedEffect(Unit) { fr.requestFocus() }
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = C.card,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BText("Quick add", 22, 300)
            BField(text, { text = it }, "e.g. Call Sam tomorrow at 5pm", focus = fr, size = 16)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Task", "Event", "Note").forEachIndexed { i, n -> Pill(n, type == i, { type = i }, onCard = true) }
            }
            if (text.isNotBlank()) BText(previewQuick(parsed), 13, 500, C.accent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onDetails(parsed.copy(title = if (text.isBlank()) "" else parsed.title)) }) {
                    BText("More options", 14, 500, C.muted)
                }
                Box(
                    Modifier.background(if (text.isBlank()) C.accent.copy(alpha = 0.4f) else C.accent, RoundedCornerShape(50))
                        .clickable(enabled = text.isNotBlank()) { onAdd(parsed) }
                        .padding(horizontal = 28.dp, vertical = 12.dp)
                ) { BText("Add", 15, 600, C.onAccent) }
            }
        }
    }
}
'@

W 'app\src\main\java\com\bipminder\app\MainViewModel.kt' @'
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
'@

W 'app\src\main\java\com\bipminder\app\Progress.kt' @'
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
'@

W 'app\src\main\java\com\bipminder\app\Repo.kt' @'
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
'@

W 'app\src\main\java\com\bipminder\app\Settings.kt' @'
package com.bipminder.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bip_settings")

data class AppSettings(
    val theme: Int = 0,            // 0 system, 1 light, 2 dark
    val snooze: Int = 10,
    val defaultReminder: Int = 0,  // -1 none
    val allDayMin: Int = 540,
    val notifications: Boolean = true,
    val weekMonday: Boolean = true,
    val name: String = ""
)

private object K {
    val theme = intPreferencesKey("theme")
    val snooze = intPreferencesKey("snooze")
    val defRem = intPreferencesKey("def_rem")
    val allDay = intPreferencesKey("all_day_min")
    val notif = booleanPreferencesKey("notif")
    val monday = booleanPreferencesKey("monday")
    val name = stringPreferencesKey("name")
}

fun Context.settingsFlow(): Flow<AppSettings> = applicationContext.dataStore.data.map { p ->
    AppSettings(
        theme = p[K.theme] ?: 0,
        snooze = p[K.snooze] ?: 10,
        defaultReminder = p[K.defRem] ?: 0,
        allDayMin = p[K.allDay] ?: 540,
        notifications = p[K.notif] ?: true,
        weekMonday = p[K.monday] ?: true,
        name = p[K.name] ?: ""
    )
}

suspend fun Context.saveSettings(s: AppSettings) {
    applicationContext.dataStore.edit { p ->
        p[K.theme] = s.theme
        p[K.snooze] = s.snooze
        p[K.defRem] = s.defaultReminder
        p[K.allDay] = s.allDayMin
        p[K.notif] = s.notifications
        p[K.monday] = s.weekMonday
        p[K.name] = s.name
    }
}
'@

W 'app\src\main\java\com\bipminder\app\SettingsScreen.kt' @'
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
'@

W 'app\src\main\java\com\bipminder\app\Tasks.kt' @'
package com.bipminder.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private fun statusOf(i: Item, now: Long) = when { i.completed -> 2; i.isOverdue(now) -> 3; else -> 1 }

@Composable
fun TasksScreen(
    all: List<Item>, onOpen: (Item) -> Unit, onToggle: (Item) -> Unit,
    onDuplicate: (Item) -> Unit, onDelete: (Item) -> Unit
) {
    var status by rememberSaveable { mutableStateOf(0) }
    var typeF by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    val list = remember(all, status, typeF, query, sort) {
        val q = query.trim().lowercase()
        all.filter { i ->
            val typeOk = when (typeF) { 1 -> i.type == Type.TASK; 2 -> i.type == Type.EVENT; 3 -> i.type == Type.NOTE; else -> true }
            val statusOk = if (status == 0) true else i.type != Type.NOTE && statusOf(i, now) == status
            val qOk = q.isEmpty() || i.title.lowercase().contains(q) || i.description.lowercase().contains(q) ||
                i.tags.lowercase().contains(q) || i.category.lowercase().contains(q)
            typeOk && statusOk && qOk
        }.let { l ->
            when (sort) {
                1 -> l.sortedWith(compareByDescending<Item> { it.priority }.thenBy { it.day }.thenBy { it.startMin })
                2 -> l.sortedBy { it.title.lowercase() }
                3 -> l.sortedByDescending { it.createdAt }
                else -> l.sortedWith(compareBy<Item> { it.completed }.thenBy { it.day }.thenBy { if (it.allDay) 0 else it.startMin })
            }
        }
    }
    val sortNames = listOf("Date", "Priority", "Title", "Newest")

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Tasks") {
            CircleBtn(Icons.Outlined.Search, { searching = !searching; if (!searching) query = "" }, desc = "Search")
            Box {
                CircleBtn(Icons.Outlined.Sort, { sortMenu = true }, desc = "Sort")
                DropdownMenu(sortMenu, { sortMenu = false }, modifier = Modifier.background(C.card)) {
                    sortNames.forEachIndexed { i, n ->
                        DropdownMenuItem(
                            text = { BText("Sort: $n", 14, if (sort == i) 600 else 400, if (sort == i) C.accent else C.text) },
                            onClick = { sort = i; sortMenu = false })
                    }
                }
            }
        }
        if (searching) { SearchField(query, { query = it }, { searching = false; query = "" }); Spacer(Modifier.height(10.dp)) }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val names = listOf("All", "In Progress", "Completed", "Overdue")
            items(names.size) { i -> Pill(names[i], status == i, { status = i }) }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val names = listOf("All types", "Tasks", "Events", "Notes")
            items(names.size) { i -> Pill(names[i], typeF == i, { typeF = i }) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (list.isEmpty()) {
                item {
                    if (query.isNotBlank()) EmptyState(Icons.Outlined.Search, "No matches", "Try a different search or filter.")
                    else EmptyState(Icons.Outlined.Checklist, "Nothing here yet", "Tap + to add a task, event or note.")
                }
            } else {
                items(list, key = { it.id }) { i ->
                    ItemCard(i, i.day, true, true, { onOpen(i) }, { onToggle(i) }, { onDuplicate(i) }, { onDelete(i) })
                }
            }
        }
    }
}
'@

W 'app\src\main\java\com\bipminder\app\Theme.kt' @'
package com.bipminder.app

import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*

@Immutable
class BipColors(
    val bg: Color, val card: Color, val text: Color, val muted: Color, val accent: Color,
    val onAccent: Color, val chip: Color, val panel: Color, val line: Color, val dark: Boolean
)

val LightBip = BipColors(
    bg = Color(0xFFB8BDF2), card = Color(0xFFFFFFFF), text = Color(0xFF242326), muted = Color(0xFF55535F),
    accent = Color(0xFF6269C5), onAccent = Color.White, chip = Color(0x99FFFFFF),
    panel = Color(0xFFCDD0F7), line = Color(0x1F242326), dark = false
)
val DarkBip = BipColors(
    bg = Color(0xFF17172A), card = Color(0xFF24243B), text = Color(0xFFF1F0F8), muted = Color(0xFFA6A4BA),
    accent = Color(0xFF7C83DE), onAccent = Color.White, chip = Color(0xFF2B2B45),
    panel = Color(0xFF2D2D4A), line = Color(0x24FFFFFF), dark = true
)

val LocalBip = staticCompositionLocalOf { LightBip }
val LocalInter = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }

val C: BipColors
    @Composable @ReadOnlyComposable get() = LocalBip.current

object Pri {
    val high = Color(0xFFE5484D)
    val med = Color(0xFFF2A33A)
    val low = Color(0xFF46A86B)
    fun color(p: Int): Color = when (p) { 2 -> high; 1 -> med; else -> low }
    fun label(p: Int): String = when (p) { 2 -> "High"; 1 -> "Medium"; else -> "Low" }
}

@OptIn(ExperimentalTextApi::class)
fun loadInter(ctx: Context): FontFamily = try {
    if (ctx.assets.list("")?.contains("inter.ttf") == true) {
        FontFamily(listOf(300, 400, 500, 600, 700).map { w ->
            Font("inter.ttf", ctx.assets, FontWeight(w), FontStyle.Normal,
                FontVariation.Settings(FontVariation.weight(w)))
        })
    } else FontFamily.SansSerif
} catch (e: Throwable) { FontFamily.SansSerif }

@Composable
fun BipTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkBip else LightBip
    val ctx = LocalContext.current
    val fam = remember { loadInter(ctx) }
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
        surface = c.card, onSurface = c.text, surfaceVariant = c.panel, onSurfaceVariant = c.muted,
        outline = c.line, surfaceContainerHigh = c.card, surfaceContainerHighest = c.panel,
        surfaceContainer = c.card, surfaceContainerLow = c.card
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
        surface = c.card, onSurface = c.text, surfaceVariant = c.panel, onSurfaceVariant = c.muted,
        outline = c.line, surfaceContainerHigh = c.card, surfaceContainerHighest = c.panel,
        surfaceContainer = c.card, surfaceContainerLow = c.card
    )
    CompositionLocalProvider(LocalBip provides c, LocalInter provides fam) {
        MaterialTheme(colorScheme = scheme) {
            ProvideTextStyle(TextStyle(fontFamily = fam, color = c.text)) { content() }
        }
    }
}
'@

W 'app\src\main\java\com\bipminder\app\TimeUtil.kt' @'
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
'@

W 'app\src\main\res\drawable\ic_launcher_foreground.xml' @'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <group android:scaleX="0.55" android:scaleY="0.55" android:pivotX="12" android:pivotY="12">
        <path android:fillColor="#FFFFFF"
            android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z" />
    </group>
</vector>
'@

W 'app\src\main\res\drawable\ic_notif.xml' @'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z" />
</vector>
'@

W 'app\src\main\res\mipmap-anydpi-v26\ic_launcher.xml' @'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'@

W 'app\src\main\res\mipmap-anydpi-v26\ic_launcher_round.xml' @'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'@

W 'app\src\main\res\values-night\colors.xml' @'
<resources>
    <color name="window_bg">#17172A</color>
</resources>
'@

W 'app\src\main\res\values-night\themes.xml' @'
<resources>
    <style name="Theme.BipMinder" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/window_bg</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
'@

W 'app\src\main\res\values\colors.xml' @'
<resources>
    <color name="ic_launcher_background">#6269C5</color>
    <color name="window_bg">#B8BDF2</color>
</resources>
'@

W 'app\src\main\res\values\strings.xml' @'
<resources>
    <string name="app_name">BipMinder</string>
</resources>
'@

W 'app\src\main\res\values\themes.xml' @'
<resources>
    <style name="Theme.BipMinder" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/window_bg</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
'@

W 'app\src\main\res\xml\file_paths.xml' @'
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="camera" path="camera/" />
</paths>
'@

W 'build.gradle.kts' @'
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
}
'@

W 'gradle.properties' @'
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
'@

W 'settings.gradle.kts' @'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BipMinder"
include(":app")
'@


# ---------- local.properties ----------
W 'local.properties' ("sdk.dir=" + ($Sdk -replace '\\', '/'))

# ---------- JDK ----------
Write-Host '==> Checking JDK' -ForegroundColor Cyan
function Test-Jdk($h) {
    if (-not $h) { return $false }
    $rel = Join-Path $h 'release'
    if (-not (Test-Path $rel)) { return $false }
    $t = Get-Content $rel -Raw
    if ($t -match 'JAVA_VERSION="(\d+)') { $v = [int]$Matches[1]; return ($v -ge 17 -and $v -le 21) }
    return $false
}
if (Test-Jdk $env:JAVA_HOME) { $jdk = $env:JAVA_HOME }
else {
    $local = Get-ChildItem $Tools -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $local) {
        Write-Host '    Downloading Temurin JDK 17...'
        $z = Join-Path $Tools 'jdk17.zip'
        Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $z
        Expand-Archive $z $Tools -Force
        Remove-Item $z -Force
        $local = Get-ChildItem $Tools -Directory -Filter 'jdk-17*' | Select-Object -First 1
    }
    $jdk = $local.FullName
}
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

# ---------- Android SDK ----------
Write-Host '==> Checking Android SDK' -ForegroundColor Cyan
$sdkm = Join-Path $Sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $sdkm)) {
    Write-Host '    Downloading Android command-line tools...'
    $z = Join-Path $Tools 'cmdline-tools.zip'
    Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $z
    $tmp = Join-Path $Tools 'cmdtmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive $z $tmp -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $Sdk 'cmdline-tools') | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $Sdk 'cmdline-tools\latest')
    Remove-Item $tmp -Recurse -Force
    Remove-Item $z -Force
}
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk

$prev = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
Write-Host '    Accepting licenses and installing platform 34 + build-tools 34.0.0...'
(1..80 | ForEach-Object { 'y' }) | & $sdkm "--sdk_root=$Sdk" --licenses | Out-Null
& $sdkm "--sdk_root=$Sdk" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
$code = $LASTEXITCODE
$ErrorActionPreference = $prev
if ($code -ne 0) { throw 'sdkmanager failed' }

# ---------- Gradle wrapper ----------
Write-Host '==> Preparing Gradle wrapper' -ForegroundColor Cyan
$gv = '8.9'
$gdir = Join-Path $Tools "gradle-$gv"
if (-not (Test-Path (Join-Path $gdir 'bin\gradle.bat'))) {
    Write-Host '    Downloading Gradle...'
    $z = Join-Path $Tools 'gradle.zip'
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$gv-bin.zip" -OutFile $z
    Expand-Archive $z $Tools -Force
    Remove-Item $z -Force
}
if (-not (Test-Path (Join-Path $Root 'gradlew.bat'))) {
    $wtmp = Join-Path $Tools 'wrapper-tmp'
    if (Test-Path $wtmp) { Remove-Item $wtmp -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $wtmp | Out-Null
    Push-Location $wtmp
    $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    & (Join-Path $gdir 'bin\gradle.bat') wrapper --gradle-version $gv --no-daemon
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prev
    Pop-Location
    if ($code -ne 0) { throw 'Gradle wrapper generation failed' }
    Copy-Item (Join-Path $wtmp 'gradlew') $Root -Force
    Copy-Item (Join-Path $wtmp 'gradlew.bat') $Root -Force
    Copy-Item (Join-Path $wtmp 'gradle') $Root -Recurse -Force
}

# ---------- Inter font (bundled into the APK as an asset) ----------
Write-Host '==> Fetching Inter font' -ForegroundColor Cyan
$assets = Join-Path $Root 'app\src\main\assets'
New-Item -ItemType Directory -Force -Path $assets | Out-Null
$font = Join-Path $assets 'inter.ttf'
if (-not (Test-Path $font) -or (Get-Item $font).Length -lt 100000) {
    $urls = @(
        'https://raw.githubusercontent.com/google/fonts/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf',
        'https://github.com/google/fonts/raw/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf'
    )
    foreach ($u in $urls) {
        try {
            Invoke-WebRequest $u -OutFile $font
            if ((Get-Item $font).Length -gt 100000) { break }
        } catch { }
    }
    if (-not (Test-Path $font) -or (Get-Item $font).Length -lt 100000) {
        Remove-Item $font -Force -ErrorAction SilentlyContinue
        Write-Host '    Inter download failed; the app will use the system sans-serif font.' -ForegroundColor Yellow
    }
}

# ---------- Build ----------
Write-Host '==> Building debug APK' -ForegroundColor Cyan
Push-Location $Root
$prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
& .\gradlew.bat assembleDebug --console=plain
$code = $LASTEXITCODE
$ErrorActionPreference = $prev
Pop-Location
if ($code -ne 0) { throw "Gradle build failed (exit code $code)" }

$apk = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw 'APK not found after build' }
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
Write-Host ''
Write-Host "APK:  $apk" -ForegroundColor Green
Write-Host 'Install (USB debugging on, phone connected):' -ForegroundColor Green
Write-Host "  & '$adb' devices"
Write-Host "  & '$adb' install -r '$apk'"
