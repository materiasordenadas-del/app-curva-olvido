package com.curvadeolvido.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.curvadeolvido.app.domain.FsrsMemoryEngine;
import com.curvadeolvido.app.domain.ReviewEvent;
import com.curvadeolvido.app.domain.ReviewRating;
import com.curvadeolvido.app.domain.ReviewScheduleStatus;
import com.curvadeolvido.app.domain.ScheduledReview;
import com.curvadeolvido.app.domain.StudyTopic;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class StudyRepository {
    public interface Callback<T> {
        void onResult(T value);
    }

    public interface ErrorCallback {
        void onError(Throwable error);
    }

    private final StudyDao dao;
    private final FsrsMemoryEngine engine;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public StudyRepository(Context context, FsrsMemoryEngine engine) {
        this.dao = StudyDatabase.get(context).studyDao();
        this.engine = engine;
    }

    public void initialize(
            SharedPreferences legacyPreferences,
            Callback<List<StudyTopic>> callback,
            ErrorCallback errorCallback) {
        io.execute(
                () -> {
                    try {
                        if (dao.countTopics() == 0
                                && !legacyPreferences.getBoolean("room_migrated", false)) {
                            migrateLegacy(legacyPreferences);
                        }
                        dao.markOverdueSchedulesMissed(System.currentTimeMillis());
                        List<StudyTopic> topics = mapAggregates(dao.loadTopicAggregates());
                        boolean repairedInitialLearning = false;
                        for (StudyTopic topic : topics) {
                            if (engine.ensureInitialLearning(topic)) {
                                dao.upsertTopic(toEntity(topic));
                                if (topic.activeScheduleId > 0) {
                                    dao.resetInitialSchedule(
                                            topic.activeScheduleId,
                                            topic.nextReviewAt,
                                            topic.scheduleGraceDeadline);
                                }
                                repairedInitialLearning = true;
                            }
                        }
                        if (repairedInitialLearning) {
                            topics = mapAggregates(dao.loadTopicAggregates());
                        }
                        List<StudyTopic> loadedTopics = topics;
                        main.post(() -> callback.onResult(loadedTopics));
                    } catch (Throwable error) {
                        main.post(() -> errorCallback.onError(error));
                    }
                });
    }

    public void createTopic(
            StudyTopic topic, Runnable onComplete, ErrorCallback errorCallback) {
        io.execute(
                () -> {
                    try {
                        ScheduledReview next = scheduleFromTopic(topic);
                        dao.createTopicWithSchedule(toEntity(topic), toEntity(next));
                        TopicEntity persisted = dao.findTopic(topic.id);
                        topic.activeScheduleId = persisted.activeScheduleId;
                        next.id = topic.activeScheduleId;
                        topic.scheduledReviews.add(next);
                        main.post(onComplete);
                    } catch (Throwable error) {
                        main.post(() -> errorCallback.onError(error));
                    }
                });
    }

    public void updateTopic(
            StudyTopic topic, Runnable onComplete, ErrorCallback errorCallback) {
        io.execute(
                () -> {
                    try {
                        dao.upsertTopic(toEntity(topic));
                        main.post(onComplete);
                    } catch (Throwable error) {
                        main.post(() -> errorCallback.onError(error));
                    }
                });
    }

    public void recordReview(
            StudyTopic topic,
            ReviewEvent event,
            ScheduledReview nextSchedule,
            Runnable onComplete,
            ErrorCallback errorCallback) {
        io.execute(
                () -> {
                    try {
                        long eventId =
                                dao.recordReview(
                                        toEntity(topic),
                                        toEntity(event),
                                        toEntity(nextSchedule),
                                        event.scheduledReviewId,
                                        event.completionStatus.name(),
                                        event.reviewedAt);
                        event.id = eventId;
                        TopicEntity persisted = dao.findTopic(topic.id);
                        topic.activeScheduleId = persisted.activeScheduleId;
                        nextSchedule.id = topic.activeScheduleId;
                        for (ScheduledReview scheduled : topic.scheduledReviews) {
                            if (scheduled.id == event.scheduledReviewId) {
                                scheduled.status = event.completionStatus;
                                scheduled.completedAt = event.reviewedAt;
                                if (event.wasPreviouslyMissed && scheduled.missedAt == 0) {
                                    scheduled.missedAt = event.reviewedAt;
                                }
                            }
                        }
                        topic.scheduledReviews.add(nextSchedule);
                        topic.scheduleStatus = ReviewScheduleStatus.SCHEDULED;
                        topic.scheduleGraceDeadline = nextSchedule.graceDeadline;
                        main.post(onComplete);
                    } catch (Throwable error) {
                        main.post(() -> errorCallback.onError(error));
                    }
                });
    }

    public void loadDueTopics(
            long now, Callback<List<StudyTopic>> callback, ErrorCallback errorCallback) {
        io.execute(
                () -> {
                    try {
                        dao.markOverdueSchedulesMissed(now);
                        List<TopicEntity> due = dao.loadDueTopics(now);
                        List<StudyTopic> mapped = new ArrayList<>();
                        for (TopicEntity entity : due) {
                            mapped.add(fromEntity(entity));
                        }
                        main.post(() -> callback.onResult(mapped));
                    } catch (Throwable error) {
                        main.post(() -> errorCallback.onError(error));
                    }
                });
    }

    public void close() {
        io.shutdown();
    }

    private void migrateLegacy(SharedPreferences preferences) throws Exception {
        JSONArray legacyTopics = new JSONArray(preferences.getString("topics", "[]"));
        long now = System.currentTimeMillis();

        for (int index = 0; index < legacyTopics.length(); index++) {
            JSONObject old = legacyTopics.getJSONObject(index);
            long rawId = old.optLong("id", now + index);
            long id = rawId < 0 ? Integer.toUnsignedLong((int) rawId) : rawId;
            if (id == 0) {
                id = now + index;
            }

            StudyTopic topic = new StudyTopic();
            topic.id = id;
            topic.name = old.optString("name", "");
            topic.subject = old.optString("subject", "");
            topic.section = old.optString("section", "");
            topic.notes = old.optString("notes", "");
            topic.createdAt = old.optLong("created", now);
            int oldProgress = old.optInt("progress", 0);
            double oldConsolidation = old.optDouble("consolidation", oldProgress * 0.28);
            long oldLastStudy = old.optLong("lastStudy", topic.createdAt);

            engine.initializeLegacyTopic(
                    topic, oldProgress, oldConsolidation, oldLastStudy);

            dao.createTopicWithSchedule(toEntity(topic), toEntity(scheduleFromTopic(topic)));
            TopicEntity persisted = dao.findTopic(topic.id);
            topic.activeScheduleId = persisted.activeScheduleId;

            JSONArray logs = old.optJSONArray("logs");
            if (logs == null) {
                continue;
            }

            for (int logIndex = 0; logIndex < logs.length(); logIndex++) {
                JSONObject oldLog = logs.getJSONObject(logIndex);
                ReviewEventEntity event = new ReviewEventEntity();
                event.topicId = topic.id;
                event.scheduledReviewId = 0;
                event.scheduledAt = 0;
                event.reviewedAt = oldLastStudy;
                event.completionStatus = ReviewScheduleStatus.MIGRATED.name();
                event.rating = "LEGACY";
                event.coverageAdded = oldLog.optInt("percent", 0);
                event.studied = oldLog.optString("studied", "");
                event.method = oldLog.optString("method", "");
                event.remaining = oldLog.optString("remaining", "");
                event.photoBase64 = oldLog.optString("photo", "");
                dao.insertReviewEvent(event);
            }
        }

        preferences.edit().putBoolean("room_migrated", true).apply();
    }

    private List<StudyTopic> mapAggregates(List<TopicAggregate> aggregates) {
        List<StudyTopic> topics = new ArrayList<>();
        for (TopicAggregate aggregate : aggregates) {
            StudyTopic topic = fromEntity(aggregate.topic);

            if (aggregate.reviewEvents != null) {
                aggregate.reviewEvents.sort(
                        Comparator.comparingLong((ReviewEventEntity item) -> item.reviewedAt)
                                .reversed());
                for (ReviewEventEntity event : aggregate.reviewEvents) {
                    topic.reviewEvents.add(fromEntity(event));
                }
            }

            if (aggregate.scheduledReviews != null) {
                aggregate.scheduledReviews.sort(
                        Comparator.comparingLong(
                                (ScheduledReviewEntity item) -> item.scheduledAt));
                for (ScheduledReviewEntity schedule : aggregate.scheduledReviews) {
                    ScheduledReview mapped = fromEntity(schedule);
                    topic.scheduledReviews.add(mapped);
                    if (schedule.id == topic.activeScheduleId) {
                        topic.scheduleStatus = mapped.status;
                        topic.scheduleGraceDeadline = mapped.graceDeadline;
                    }
                }
            }
            topics.add(topic);
        }
        return topics;
    }

    private static TopicEntity toEntity(StudyTopic topic) {
        TopicEntity entity = new TopicEntity();
        entity.id = topic.id;
        entity.name = safe(topic.name);
        entity.subject = safe(topic.subject);
        entity.section = safe(topic.section);
        entity.notes = safe(topic.notes);
        entity.coveragePercent = topic.coveragePercent;
        entity.fsrsCardJson = safe(topic.fsrsCardJson);
        entity.createdAt = topic.createdAt;
        entity.lastReviewAt = topic.lastReviewAt;
        entity.nextReviewAt = topic.nextReviewAt;
        entity.lapseCount = topic.lapseCount;
        entity.activeScheduleId = topic.activeScheduleId;
        return entity;
    }

    private static StudyTopic fromEntity(TopicEntity entity) {
        StudyTopic topic = new StudyTopic();
        topic.id = entity.id;
        topic.name = entity.name;
        topic.subject = entity.subject;
        topic.section = entity.section;
        topic.notes = entity.notes;
        topic.coveragePercent = entity.coveragePercent;
        topic.fsrsCardJson = entity.fsrsCardJson;
        topic.createdAt = entity.createdAt;
        topic.lastReviewAt = entity.lastReviewAt;
        topic.nextReviewAt = entity.nextReviewAt;
        topic.lapseCount = entity.lapseCount;
        topic.activeScheduleId = entity.activeScheduleId;
        return topic;
    }

    private static ReviewEventEntity toEntity(ReviewEvent event) {
        ReviewEventEntity entity = new ReviewEventEntity();
        entity.id = event.id;
        entity.topicId = event.topicId;
        entity.scheduledReviewId = event.scheduledReviewId;
        entity.scheduledAt = event.scheduledAt;
        entity.reviewedAt = event.reviewedAt;
        entity.completionStatus = event.completionStatus.name();
        entity.rating = event.rating.name();
        entity.wasPreviouslyMissed = event.wasPreviouslyMissed;
        entity.retrievabilityBefore = event.retrievabilityBefore;
        entity.retrievabilityAfter = event.retrievabilityAfter;
        entity.stabilityBefore = event.stabilityBefore;
        entity.stabilityAfter = event.stabilityAfter;
        entity.difficultyBefore = event.difficultyBefore;
        entity.difficultyAfter = event.difficultyAfter;
        entity.coverageAdded = event.coverageAdded;
        entity.studied = safe(event.studied);
        entity.method = safe(event.method);
        entity.remaining = safe(event.remaining);
        entity.photoBase64 = safe(event.photoBase64);
        return entity;
    }

    private static ReviewEvent fromEntity(ReviewEventEntity entity) {
        ReviewEvent event = new ReviewEvent();
        event.id = entity.id;
        event.topicId = entity.topicId;
        event.scheduledReviewId = entity.scheduledReviewId;
        event.scheduledAt = entity.scheduledAt;
        event.reviewedAt = entity.reviewedAt;
        event.completionStatus = parseStatus(entity.completionStatus);
        event.rating = parseRating(entity.rating);
        event.wasPreviouslyMissed = entity.wasPreviouslyMissed;
        event.retrievabilityBefore = entity.retrievabilityBefore;
        event.retrievabilityAfter = entity.retrievabilityAfter;
        event.stabilityBefore = entity.stabilityBefore;
        event.stabilityAfter = entity.stabilityAfter;
        event.difficultyBefore = entity.difficultyBefore;
        event.difficultyAfter = entity.difficultyAfter;
        event.coverageAdded = entity.coverageAdded;
        event.studied = entity.studied;
        event.method = entity.method;
        event.remaining = entity.remaining;
        event.photoBase64 = entity.photoBase64;
        return event;
    }

    private static ScheduledReviewEntity toEntity(ScheduledReview review) {
        ScheduledReviewEntity entity = new ScheduledReviewEntity();
        entity.id = review.id;
        entity.topicId = review.topicId;
        entity.scheduledAt = review.scheduledAt;
        entity.graceDeadline = review.graceDeadline;
        entity.status = review.status.name();
        entity.missedAt = review.missedAt;
        entity.completedAt = review.completedAt;
        return entity;
    }

    private static ScheduledReview fromEntity(ScheduledReviewEntity entity) {
        ScheduledReview review = new ScheduledReview();
        review.id = entity.id;
        review.topicId = entity.topicId;
        review.scheduledAt = entity.scheduledAt;
        review.graceDeadline = entity.graceDeadline;
        review.status = parseStatus(entity.status);
        review.missedAt = entity.missedAt;
        review.completedAt = entity.completedAt;
        return review;
    }

    private static ScheduledReview scheduleFromTopic(StudyTopic topic) {
        ScheduledReview schedule = new ScheduledReview();
        schedule.topicId = topic.id;
        schedule.scheduledAt = topic.nextReviewAt;
        schedule.graceDeadline = topic.scheduleGraceDeadline;
        schedule.status = ReviewScheduleStatus.SCHEDULED;
        return schedule;
    }

    private static ReviewScheduleStatus parseStatus(String value) {
        try {
            return ReviewScheduleStatus.valueOf(value);
        } catch (Exception ignored) {
            return ReviewScheduleStatus.MIGRATED;
        }
    }

    private static ReviewRating parseRating(String value) {
        try {
            return ReviewRating.valueOf(value);
        } catch (Exception ignored) {
            return ReviewRating.GOOD;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
