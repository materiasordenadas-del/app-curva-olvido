package com.curvadeolvido.app.domain;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.CardAndReviewLog;
import io.github.openspacedrepetition.Scheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class FsrsMemoryEngine {
    public static final double DEFAULT_DESIRED_RETENTION = 0.90;
    public static final long REVIEW_GRACE_PERIOD_MS = Duration.ofHours(24).toMillis();

    private final Scheduler scheduler;
    private final double desiredRetention;

    public FsrsMemoryEngine() {
        this(DEFAULT_DESIRED_RETENTION);
    }

    public FsrsMemoryEngine(double desiredRetention) {
        if (desiredRetention <= 0.0 || desiredRetention >= 1.0) {
            throw new IllegalArgumentException("desiredRetention debe estar entre 0 y 1");
        }
        this.desiredRetention = desiredRetention;
        scheduler =
                Scheduler.builder()
                        .desiredRetention(desiredRetention)
                        .learningSteps(new Duration[] {})
                        .relearningSteps(new Duration[] {})
                        .enableFuzzing(false)
                        .build();
    }

    public double desiredRetention() {
        return desiredRetention;
    }

    public StudyTopic newTopic(
            long id,
            String name,
            String subject,
            String section,
            String notes,
            long createdAt) {
        Card card = Card.builder().build();
        StudyTopic topic = new StudyTopic();
        topic.id = id;
        topic.name = safe(name);
        topic.subject = safe(subject);
        topic.section = safe(section);
        topic.notes = safe(notes);
        topic.createdAt = createdAt;
        topic.fsrsCardJson = card.toJson();
        topic.nextReviewAt = Math.max(createdAt, card.getDue().toEpochMilli());
        topic.scheduleGraceDeadline = topic.nextReviewAt + REVIEW_GRACE_PERIOD_MS;
        topic.scheduleStatus = ReviewScheduleStatus.SCHEDULED;
        return topic;
    }

    public ReviewResult review(
            StudyTopic topic,
            ReviewRating rating,
            long reviewedAt,
            int coverageAdded,
            String studied,
            String method,
            String remaining,
            String photoBase64) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(rating, "rating");

        Card beforeCard = cardFrom(topic);
        Instant reviewInstant = Instant.ofEpochMilli(reviewedAt);
        double retrievabilityBefore = retrievability(beforeCard, reviewInstant);
        double stabilityBefore = nullableDouble(beforeCard.getStability());
        double difficultyBefore = nullableDouble(beforeCard.getDifficulty());

        long scheduledAt =
                topic.nextReviewAt > 0
                        ? topic.nextReviewAt
                        : Math.max(topic.createdAt, beforeCard.getDue().toEpochMilli());
        long graceDeadline =
                topic.scheduleGraceDeadline > 0
                        ? topic.scheduleGraceDeadline
                        : scheduledAt + REVIEW_GRACE_PERIOD_MS;
        boolean wasMissed =
                topic.scheduleStatus == ReviewScheduleStatus.MISSED
                        || reviewedAt > graceDeadline;
        ReviewScheduleStatus completionStatus =
                wasMissed
                        ? ReviewScheduleStatus.COMPLETED_LATE
                        : ReviewScheduleStatus.COMPLETED_ON_TIME;

        CardAndReviewLog fsrsResult =
                scheduler.reviewCard(beforeCard, rating.toFsrsRating(), reviewInstant, null);
        Card afterCard = fsrsResult.card();

        double retrievabilityAfter = retrievability(afterCard, reviewInstant);
        double stabilityAfter = nullableDouble(afterCard.getStability());
        double difficultyAfter = nullableDouble(afterCard.getDifficulty());

        topic.coveragePercent =
                Math.min(100, Math.max(0, topic.coveragePercent + Math.max(0, coverageAdded)));
        topic.fsrsCardJson = afterCard.toJson();
        topic.lastReviewAt = reviewedAt;
        topic.nextReviewAt = afterCard.getDue().toEpochMilli();
        topic.scheduleGraceDeadline = topic.nextReviewAt + REVIEW_GRACE_PERIOD_MS;
        topic.scheduleStatus = ReviewScheduleStatus.SCHEDULED;
        if (rating.isRetrievalFailure()) {
            topic.lapseCount += 1;
        }

        ReviewEvent event = new ReviewEvent();
        event.topicId = topic.id;
        event.scheduledReviewId = topic.activeScheduleId;
        event.scheduledAt = scheduledAt;
        event.reviewedAt = reviewedAt;
        event.completionStatus = completionStatus;
        event.rating = rating;
        event.wasPreviouslyMissed = wasMissed;
        event.retrievabilityBefore = retrievabilityBefore;
        event.retrievabilityAfter = retrievabilityAfter;
        event.stabilityBefore = stabilityBefore;
        event.stabilityAfter = stabilityAfter;
        event.difficultyBefore = difficultyBefore;
        event.difficultyAfter = difficultyAfter;
        event.coverageAdded = Math.max(0, coverageAdded);
        event.studied = safe(studied);
        event.method = safe(method);
        event.remaining = safe(remaining);
        event.photoBase64 = safe(photoBase64);

        ScheduledReview next = new ScheduledReview();
        next.topicId = topic.id;
        next.scheduledAt = topic.nextReviewAt;
        next.graceDeadline = topic.scheduleGraceDeadline;
        next.status = ReviewScheduleStatus.SCHEDULED;

        topic.reviewEvents.add(0, event);
        return new ReviewResult(topic, event, next);
    }

    public MemorySnapshot snapshot(StudyTopic topic, long measuredAt) {
        Card card = cardFrom(topic);
        return new MemorySnapshot(
                retrievability(card, Instant.ofEpochMilli(measuredAt)),
                nullableDouble(card.getStability()),
                nullableDouble(card.getDifficulty()),
                measuredAt);
    }

    public double retrievability(StudyTopic topic, long measuredAt) {
        return snapshot(topic, measuredAt).retrievability;
    }

    public double[] retrievabilitySeries(
            StudyTopic topic, long startAt, long stepMillis, int sampleCount) {
        if (sampleCount < 1) {
            throw new IllegalArgumentException("sampleCount debe ser al menos 1");
        }
        Card card = cardFrom(topic);
        double[] values = new double[sampleCount];
        for (int index = 0; index < sampleCount; index++) {
            long measuredAt = startAt + stepMillis * index;
            values[index] = retrievability(card, Instant.ofEpochMilli(measuredAt));
        }
        return values;
    }

    public boolean isDue(StudyTopic topic, long now) {
        return topic.nextReviewAt > 0 && now >= topic.nextReviewAt;
    }

    public void initializeLegacyTopic(
            StudyTopic topic,
            int oldProgress,
            double oldConsolidation,
            long oldLastStudy) {
        topic.coveragePercent = Math.min(100, Math.max(0, oldProgress));
        Card card = Card.builder().build();

        if (oldProgress > 0) {
            ReviewRating rating;
            if (oldConsolidation >= 70 || oldProgress >= 90) {
                rating = ReviewRating.EASY;
            } else if (oldConsolidation >= 35 || oldProgress >= 50) {
                rating = ReviewRating.GOOD;
            } else {
                rating = ReviewRating.HARD;
            }
            long reviewTime = Math.max(topic.createdAt, oldLastStudy);
            CardAndReviewLog migrated =
                    scheduler.reviewCard(
                            card,
                            rating.toFsrsRating(),
                            Instant.ofEpochMilli(reviewTime),
                            null);
            card = migrated.card();
            topic.lastReviewAt = reviewTime;
        }

        topic.fsrsCardJson = card.toJson();
        topic.nextReviewAt = card.getDue().toEpochMilli();
        topic.scheduleGraceDeadline = topic.nextReviewAt + REVIEW_GRACE_PERIOD_MS;
        topic.scheduleStatus = ReviewScheduleStatus.SCHEDULED;
    }

    private Card cardFrom(StudyTopic topic) {
        if (topic.fsrsCardJson == null || topic.fsrsCardJson.isBlank()) {
            return Card.builder().build();
        }
        return Card.fromJson(topic.fsrsCardJson);
    }

    private double retrievability(Card card, Instant when) {
        if (card.getLastReview() == null) {
            return 0.0;
        }
        return clamp01(scheduler.getCardRetrievability(card, when));
    }

    private static double nullableDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
