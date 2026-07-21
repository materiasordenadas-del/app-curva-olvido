package com.curvadeolvido.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "scheduled_reviews",
        foreignKeys =
                @ForeignKey(
                        entity = TopicEntity.class,
                        parentColumns = "id",
                        childColumns = "topicId",
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index("topicId"), @Index("scheduledAt"), @Index("status")})
public class ScheduledReviewEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long topicId;
    public long scheduledAt;
    public long graceDeadline;

    @NonNull public String status = "SCHEDULED";

    public long missedAt;
    public long completedAt;
}
