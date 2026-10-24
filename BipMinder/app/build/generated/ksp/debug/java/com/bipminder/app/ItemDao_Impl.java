package com.bipminder.app;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ItemDao_Impl implements ItemDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Item> __insertionAdapterOfItem;

  private final EntityDeletionOrUpdateAdapter<Item> __deletionAdapterOfItem;

  private final EntityDeletionOrUpdateAdapter<Item> __updateAdapterOfItem;

  private final SharedSQLiteStatement __preparedStmtOfClear;

  public ItemDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfItem = new EntityInsertionAdapter<Item>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `items` (`id`,`type`,`title`,`description`,`day`,`endDay`,`startMin`,`endMin`,`allDay`,`priority`,`category`,`tags`,`recurrence`,`reminders`,`completed`,`completedAt`,`photo`,`createdAt`,`snoozeUntil`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Item entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getType());
        statement.bindString(3, entity.getTitle());
        statement.bindString(4, entity.getDescription());
        statement.bindLong(5, entity.getDay());
        statement.bindLong(6, entity.getEndDay());
        statement.bindLong(7, entity.getStartMin());
        statement.bindLong(8, entity.getEndMin());
        final int _tmp = entity.getAllDay() ? 1 : 0;
        statement.bindLong(9, _tmp);
        statement.bindLong(10, entity.getPriority());
        statement.bindString(11, entity.getCategory());
        statement.bindString(12, entity.getTags());
        statement.bindLong(13, entity.getRecurrence());
        statement.bindString(14, entity.getReminders());
        final int _tmp_1 = entity.getCompleted() ? 1 : 0;
        statement.bindLong(15, _tmp_1);
        statement.bindLong(16, entity.getCompletedAt());
        statement.bindString(17, entity.getPhoto());
        statement.bindLong(18, entity.getCreatedAt());
        statement.bindLong(19, entity.getSnoozeUntil());
      }
    };
    this.__deletionAdapterOfItem = new EntityDeletionOrUpdateAdapter<Item>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `items` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Item entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfItem = new EntityDeletionOrUpdateAdapter<Item>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `items` SET `id` = ?,`type` = ?,`title` = ?,`description` = ?,`day` = ?,`endDay` = ?,`startMin` = ?,`endMin` = ?,`allDay` = ?,`priority` = ?,`category` = ?,`tags` = ?,`recurrence` = ?,`reminders` = ?,`completed` = ?,`completedAt` = ?,`photo` = ?,`createdAt` = ?,`snoozeUntil` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Item entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getType());
        statement.bindString(3, entity.getTitle());
        statement.bindString(4, entity.getDescription());
        statement.bindLong(5, entity.getDay());
        statement.bindLong(6, entity.getEndDay());
        statement.bindLong(7, entity.getStartMin());
        statement.bindLong(8, entity.getEndMin());
        final int _tmp = entity.getAllDay() ? 1 : 0;
        statement.bindLong(9, _tmp);
        statement.bindLong(10, entity.getPriority());
        statement.bindString(11, entity.getCategory());
        statement.bindString(12, entity.getTags());
        statement.bindLong(13, entity.getRecurrence());
        statement.bindString(14, entity.getReminders());
        final int _tmp_1 = entity.getCompleted() ? 1 : 0;
        statement.bindLong(15, _tmp_1);
        statement.bindLong(16, entity.getCompletedAt());
        statement.bindString(17, entity.getPhoto());
        statement.bindLong(18, entity.getCreatedAt());
        statement.bindLong(19, entity.getSnoozeUntil());
        statement.bindLong(20, entity.getId());
      }
    };
    this.__preparedStmtOfClear = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM items";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final Item i, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfItem.insertAndReturnId(i);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<Item> l, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfItem.insert(l);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final Item i, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfItem.handle(i);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final Item i, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfItem.handle(i);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clear(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClear.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClear.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Item>> all() {
    final String _sql = "SELECT * FROM items";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"items"}, new Callable<List<Item>>() {
      @Override
      @NonNull
      public List<Item> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfDay = CursorUtil.getColumnIndexOrThrow(_cursor, "day");
          final int _cursorIndexOfEndDay = CursorUtil.getColumnIndexOrThrow(_cursor, "endDay");
          final int _cursorIndexOfStartMin = CursorUtil.getColumnIndexOrThrow(_cursor, "startMin");
          final int _cursorIndexOfEndMin = CursorUtil.getColumnIndexOrThrow(_cursor, "endMin");
          final int _cursorIndexOfAllDay = CursorUtil.getColumnIndexOrThrow(_cursor, "allDay");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfRecurrence = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence");
          final int _cursorIndexOfReminders = CursorUtil.getColumnIndexOrThrow(_cursor, "reminders");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final int _cursorIndexOfPhoto = CursorUtil.getColumnIndexOrThrow(_cursor, "photo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfSnoozeUntil = CursorUtil.getColumnIndexOrThrow(_cursor, "snoozeUntil");
          final List<Item> _result = new ArrayList<Item>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Item _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final int _tmpType;
            _tmpType = _cursor.getInt(_cursorIndexOfType);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpDay;
            _tmpDay = _cursor.getLong(_cursorIndexOfDay);
            final long _tmpEndDay;
            _tmpEndDay = _cursor.getLong(_cursorIndexOfEndDay);
            final int _tmpStartMin;
            _tmpStartMin = _cursor.getInt(_cursorIndexOfStartMin);
            final int _tmpEndMin;
            _tmpEndMin = _cursor.getInt(_cursorIndexOfEndMin);
            final boolean _tmpAllDay;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAllDay);
            _tmpAllDay = _tmp != 0;
            final int _tmpPriority;
            _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTags;
            _tmpTags = _cursor.getString(_cursorIndexOfTags);
            final int _tmpRecurrence;
            _tmpRecurrence = _cursor.getInt(_cursorIndexOfRecurrence);
            final String _tmpReminders;
            _tmpReminders = _cursor.getString(_cursorIndexOfReminders);
            final boolean _tmpCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_1 != 0;
            final long _tmpCompletedAt;
            _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            final String _tmpPhoto;
            _tmpPhoto = _cursor.getString(_cursorIndexOfPhoto);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpSnoozeUntil;
            _tmpSnoozeUntil = _cursor.getLong(_cursorIndexOfSnoozeUntil);
            _item = new Item(_tmpId,_tmpType,_tmpTitle,_tmpDescription,_tmpDay,_tmpEndDay,_tmpStartMin,_tmpEndMin,_tmpAllDay,_tmpPriority,_tmpCategory,_tmpTags,_tmpRecurrence,_tmpReminders,_tmpCompleted,_tmpCompletedAt,_tmpPhoto,_tmpCreatedAt,_tmpSnoozeUntil);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object allNow(final Continuation<? super List<Item>> $completion) {
    final String _sql = "SELECT * FROM items";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Item>>() {
      @Override
      @NonNull
      public List<Item> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfDay = CursorUtil.getColumnIndexOrThrow(_cursor, "day");
          final int _cursorIndexOfEndDay = CursorUtil.getColumnIndexOrThrow(_cursor, "endDay");
          final int _cursorIndexOfStartMin = CursorUtil.getColumnIndexOrThrow(_cursor, "startMin");
          final int _cursorIndexOfEndMin = CursorUtil.getColumnIndexOrThrow(_cursor, "endMin");
          final int _cursorIndexOfAllDay = CursorUtil.getColumnIndexOrThrow(_cursor, "allDay");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfRecurrence = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence");
          final int _cursorIndexOfReminders = CursorUtil.getColumnIndexOrThrow(_cursor, "reminders");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final int _cursorIndexOfPhoto = CursorUtil.getColumnIndexOrThrow(_cursor, "photo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfSnoozeUntil = CursorUtil.getColumnIndexOrThrow(_cursor, "snoozeUntil");
          final List<Item> _result = new ArrayList<Item>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Item _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final int _tmpType;
            _tmpType = _cursor.getInt(_cursorIndexOfType);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpDay;
            _tmpDay = _cursor.getLong(_cursorIndexOfDay);
            final long _tmpEndDay;
            _tmpEndDay = _cursor.getLong(_cursorIndexOfEndDay);
            final int _tmpStartMin;
            _tmpStartMin = _cursor.getInt(_cursorIndexOfStartMin);
            final int _tmpEndMin;
            _tmpEndMin = _cursor.getInt(_cursorIndexOfEndMin);
            final boolean _tmpAllDay;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAllDay);
            _tmpAllDay = _tmp != 0;
            final int _tmpPriority;
            _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTags;
            _tmpTags = _cursor.getString(_cursorIndexOfTags);
            final int _tmpRecurrence;
            _tmpRecurrence = _cursor.getInt(_cursorIndexOfRecurrence);
            final String _tmpReminders;
            _tmpReminders = _cursor.getString(_cursorIndexOfReminders);
            final boolean _tmpCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_1 != 0;
            final long _tmpCompletedAt;
            _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            final String _tmpPhoto;
            _tmpPhoto = _cursor.getString(_cursorIndexOfPhoto);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpSnoozeUntil;
            _tmpSnoozeUntil = _cursor.getLong(_cursorIndexOfSnoozeUntil);
            _item = new Item(_tmpId,_tmpType,_tmpTitle,_tmpDescription,_tmpDay,_tmpEndDay,_tmpStartMin,_tmpEndMin,_tmpAllDay,_tmpPriority,_tmpCategory,_tmpTags,_tmpRecurrence,_tmpReminders,_tmpCompleted,_tmpCompletedAt,_tmpPhoto,_tmpCreatedAt,_tmpSnoozeUntil);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object get(final long id, final Continuation<? super Item> $completion) {
    final String _sql = "SELECT * FROM items WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Item>() {
      @Override
      @Nullable
      public Item call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfDay = CursorUtil.getColumnIndexOrThrow(_cursor, "day");
          final int _cursorIndexOfEndDay = CursorUtil.getColumnIndexOrThrow(_cursor, "endDay");
          final int _cursorIndexOfStartMin = CursorUtil.getColumnIndexOrThrow(_cursor, "startMin");
          final int _cursorIndexOfEndMin = CursorUtil.getColumnIndexOrThrow(_cursor, "endMin");
          final int _cursorIndexOfAllDay = CursorUtil.getColumnIndexOrThrow(_cursor, "allDay");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
          final int _cursorIndexOfRecurrence = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence");
          final int _cursorIndexOfReminders = CursorUtil.getColumnIndexOrThrow(_cursor, "reminders");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final int _cursorIndexOfPhoto = CursorUtil.getColumnIndexOrThrow(_cursor, "photo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfSnoozeUntil = CursorUtil.getColumnIndexOrThrow(_cursor, "snoozeUntil");
          final Item _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final int _tmpType;
            _tmpType = _cursor.getInt(_cursorIndexOfType);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpDay;
            _tmpDay = _cursor.getLong(_cursorIndexOfDay);
            final long _tmpEndDay;
            _tmpEndDay = _cursor.getLong(_cursorIndexOfEndDay);
            final int _tmpStartMin;
            _tmpStartMin = _cursor.getInt(_cursorIndexOfStartMin);
            final int _tmpEndMin;
            _tmpEndMin = _cursor.getInt(_cursorIndexOfEndMin);
            final boolean _tmpAllDay;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAllDay);
            _tmpAllDay = _tmp != 0;
            final int _tmpPriority;
            _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTags;
            _tmpTags = _cursor.getString(_cursorIndexOfTags);
            final int _tmpRecurrence;
            _tmpRecurrence = _cursor.getInt(_cursorIndexOfRecurrence);
            final String _tmpReminders;
            _tmpReminders = _cursor.getString(_cursorIndexOfReminders);
            final boolean _tmpCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_1 != 0;
            final long _tmpCompletedAt;
            _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            final String _tmpPhoto;
            _tmpPhoto = _cursor.getString(_cursorIndexOfPhoto);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpSnoozeUntil;
            _tmpSnoozeUntil = _cursor.getLong(_cursorIndexOfSnoozeUntil);
            _result = new Item(_tmpId,_tmpType,_tmpTitle,_tmpDescription,_tmpDay,_tmpEndDay,_tmpStartMin,_tmpEndMin,_tmpAllDay,_tmpPriority,_tmpCategory,_tmpTags,_tmpRecurrence,_tmpReminders,_tmpCompleted,_tmpCompletedAt,_tmpPhoto,_tmpCreatedAt,_tmpSnoozeUntil);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
