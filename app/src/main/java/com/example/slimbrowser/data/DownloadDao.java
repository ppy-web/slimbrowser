package com.example.slimbrowser.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC LIMIT :limit")
    List<DownloadRecord> recent(int limit);

    @Insert
    void insert(DownloadRecord record);

    @Query("DELETE FROM downloads")
    void clear();
}
