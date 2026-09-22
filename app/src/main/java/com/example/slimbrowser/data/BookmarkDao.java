package com.example.slimbrowser.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    List<BookmarkRecord> all();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BookmarkRecord record);

    @Query("DELETE FROM bookmarks WHERE url = :url")
    void deleteByUrl(String url);

    @Query("DELETE FROM bookmarks")
    void clear();
}
