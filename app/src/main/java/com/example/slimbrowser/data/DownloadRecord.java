package com.example.slimbrowser.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "downloads")
public class DownloadRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String fileName;
    public String url;
    public long createdAt;
    public int status;
}
