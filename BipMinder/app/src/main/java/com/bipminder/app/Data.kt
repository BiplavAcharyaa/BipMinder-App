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
