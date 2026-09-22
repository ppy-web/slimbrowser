package com.example.slimbrowser.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "bookmarks")
public class BookmarkRecord {
    @PrimaryKey
    @NonNull
    public String url;
    public String title;
    public long createdAt;
}
