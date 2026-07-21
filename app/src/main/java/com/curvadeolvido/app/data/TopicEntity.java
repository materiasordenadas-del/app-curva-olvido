package com.curvadeolvido.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "topics")
public class TopicEntity {
    @PrimaryKey public long id;

    @NonNull public String name = "";
    @NonNull public String subject = "";
    @NonNull public String section = "";
    @NonNull public String notes = "";

    public int coveragePercent;

    @NonNull public String fsrsCardJson = "";

    public long createdAt;
    public long lastReviewAt;
    public long nextReviewAt;
    public int lapseCount;
    public long activeScheduleId;
}
