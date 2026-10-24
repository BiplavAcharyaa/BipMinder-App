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
