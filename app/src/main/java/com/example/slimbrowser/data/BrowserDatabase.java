package com.example.slimbrowser.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {HistoryRecord.class, BookmarkRecord.class, DownloadRecord.class},
        version = 2,
        exportSchema = false
)
public abstract class BrowserDatabase extends RoomDatabase {
    public abstract HistoryDao historyDao();
    public abstract BookmarkDao bookmarkDao();
    public abstract DownloadDao downloadDao();

    private static volatile BrowserDatabase INSTANCE;

    public static BrowserDatabase getInstance(Context context) {
        BrowserDatabase result = INSTANCE;
        if (result == null) {
            synchronized (BrowserDatabase.class) {
                result = INSTANCE;
                if (result == null) {
                    result = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    BrowserDatabase.class,
                                    "browser_data.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                    INSTANCE = result;
                }
            }
        }
        return result;
    }

    /** Reserved migration hook: future schema changes must be explicit and non-destructive. */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE history ADD COLUMN favicon TEXT");
        }
    };
}
