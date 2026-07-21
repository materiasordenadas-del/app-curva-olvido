package com.curvadeolvido.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class StudyDao {
    @Query("SELECT COUNT(*) FROM topics")
    public abstract int countTopics();

    @Transaction
    @Query(
            "SELECT * FROM topics "
                    + "ORDER BY subject COLLATE NOCASE, section COLLATE NOCASE, name COLLATE NOCASE")
    public abstract List<TopicAggregate> loadTopicAggregates();

    @Query("SELECT * FROM topics WHERE id = :topicId LIMIT 1")
    public abstract TopicEntity findTopic(long topicId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertTopic(TopicEntity topic);

    @Insert
    public abstract long insertReviewEvent(ReviewEventEntity event);

    @Insert
    public abstract long insertScheduledReview(ScheduledReviewEntity review);

    @Query(
            "UPDATE scheduled_reviews "
                    + "SET status = :status, completedAt = :completedAt "
                    + "WHERE id = :scheduleId")
    public abstract void completeSchedule(long scheduleId, String status, long completedAt);

    @Query(
            "UPDATE scheduled_reviews "
                    + "SET status = 'MISSED', missedAt = :markedAt "
                    + "WHERE status = 'SCHEDULED' AND graceDeadline < :markedAt")
    public abstract int markOverdueSchedulesMissed(long markedAt);

    @Query(
            "UPDATE scheduled_reviews "
                    + "SET scheduledAt = :scheduledAt, graceDeadline = :graceDeadline, "
                    + "status = 'SCHEDULED', completedAt = 0, missedAt = 0 "
                    + "WHERE id = :scheduleId")
    public abstract void resetInitialSchedule(
            long scheduleId, long scheduledAt, long graceDeadline);

    @Query(
            "SELECT topics.* FROM topics "
                    + "INNER JOIN scheduled_reviews "
                    + "ON scheduled_reviews.id = topics.activeScheduleId "
                    + "WHERE scheduled_reviews.scheduledAt <= :now "
                    + "AND scheduled_reviews.status IN ('SCHEDULED', 'MISSED') "
                    + "ORDER BY scheduled_reviews.scheduledAt ASC")
    public abstract List<TopicEntity> loadDueTopics(long now);

    @Transaction
    public void createTopicWithSchedule(TopicEntity topic, ScheduledReviewEntity schedule) {
        upsertTopic(topic);
        long scheduleId = insertScheduledReview(schedule);
        topic.activeScheduleId = scheduleId;
        upsertTopic(topic);
    }

    @Transaction
    public long recordReview(
            TopicEntity topic,
            ReviewEventEntity event,
            ScheduledReviewEntity nextSchedule,
            long completedScheduleId,
            String completedStatus,
            long completedAt) {
        if (completedScheduleId > 0) {
            completeSchedule(completedScheduleId, completedStatus, completedAt);
        }
        long eventId = insertReviewEvent(event);
        long nextScheduleId = insertScheduledReview(nextSchedule);
        topic.activeScheduleId = nextScheduleId;
        upsertTopic(topic);
        return eventId;
    }
}
