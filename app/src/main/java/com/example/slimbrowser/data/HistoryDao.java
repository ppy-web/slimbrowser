package com.example.slimbrowser.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface HistoryDao {
    @Query("SELECT * FROM history WHERE privateSession = 0 ORDER BY visitedAt DESC LIMIT :limit")
    List<HistoryRecord> recent(int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HistoryRecord record);

    @Query("DELETE FROM history WHERE url = :url")
    void deleteByUrl(String url);

    @Query("DELETE FROM history")
    void clear();
}
