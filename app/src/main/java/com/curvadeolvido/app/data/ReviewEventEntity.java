package com.curvadeolvido.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "review_events",
        foreignKeys =
                @ForeignKey(
                        entity = TopicEntity.class,
                        parentColumns = "id",
                        childColumns = "topicId",
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index("topicId"), @Index("reviewedAt"), @Index("scheduledReviewId")})
public class ReviewEventEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long topicId;
    public long scheduledReviewId;
    public long scheduledAt;
    public long reviewedAt;

    @NonNull public String completionStatus = "COMPLETED_ON_TIME";
    @NonNull public String rating = "GOOD";

    public boolean wasPreviouslyMissed;

    public double retrievabilityBefore;
    public double retrievabilityAfter;
    public double stabilityBefore;
    public double stabilityAfter;
    public double difficultyBefore;
    public double difficultyAfter;

    public int coverageAdded;

    @NonNull public String studied = "";
    @NonNull public String method = "";
    @NonNull public String remaining = "";
    @NonNull public String photoBase64 = "";
}
