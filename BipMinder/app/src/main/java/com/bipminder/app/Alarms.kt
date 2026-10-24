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
