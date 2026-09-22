package com.example.slimbrowser.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "history", indices = {@Index(value = {"url"})})
public class HistoryRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String title;
    public String url;
    public long visitedAt;
    public boolean privateSession;
    public String favicon;
}
