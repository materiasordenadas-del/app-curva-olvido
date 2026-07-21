package com.curvadeolvido.app.dashboard;

import com.curvadeolvido.app.domain.FsrsMemoryEngine;
import com.curvadeolvido.app.domain.ReviewEvent;
import com.curvadeolvido.app.domain.ReviewRating;
import com.curvadeolvido.app.domain.ReviewScheduleStatus;
import com.curvadeolvido.app.domain.StudyTopic;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure operational dashboard derived from persisted topics and review events. */
public final class DashboardModel {
    public static final int LOAD_DAYS = 14;
    private static final int MAX_PRIORITIES = 5;

    public enum PriorityKind {
        MISSED,
        DUE,
        BELOW_TARGET,
        UPCOMING
    }

    public static final class PriorityItem {
        public final StudyTopic topic;
        public final PriorityKind kind;
        public final double retrievability;
        public final long scheduledAt;

        PriorityItem(
                StudyTopic topic,
                PriorityKind kind,
                double retrievability,
                long scheduledAt) {
            this.topic = topic;
            this.kind = kind;
            this.retrievability = clamp01(retrievability);
            this.scheduledAt = scheduledAt;
        }
    }

    public final long measuredAt;
    public final double targetRetention;
    public final int totalTopics;
    public final int reviewedTopics;
    public final int dueNow;
    public final int missed;
    public final int upcomingSevenDays;
    public final int belowTarget;
    public final int reviewedLastSevenDays;
    public final int completedOnTime;
    public final int completedLate;
    public final int retrievalFailures;
    public final double averageRetrievability;
    public final double punctualityRate;
    public final int[] scheduledLoad;
    public final List<PriorityItem> priorities;

    private DashboardModel(
            long measuredAt,
            double targetRetention,
            int totalTopics,
            int reviewedTopics,
            int dueNow,
            int missed,
            int upcomingSevenDays,
            int belowTarget,
            int reviewedLastSevenDays,
            int completedOnTime,
            int completedLate,
            int retrievalFailures,
            double averageRetrievability,
            double punctualityRate,
            int[] scheduledLoad,
            List<PriorityItem> priorities) {
        this.measuredAt = measuredAt;
        this.targetRetention = targetRetention;
        this.totalTopics = totalTopics;
        this.reviewedTopics = reviewedTopics;
        this.dueNow = dueNow;
        this.missed = missed;
        this.upcomingSevenDays = upcomingSevenDays;
        this.belowTarget = belowTarget;
        this.reviewedLastSevenDays = reviewedLastSevenDays;
        this.completedOnTime = completedOnTime;
        this.completedLate = completedLate;
        this.retrievalFailures = retrievalFailures;
        this.averageRetrievability = clamp01(averageRetrievability);
        this.punctualityRate = clamp01(punctualityRate);
        this.scheduledLoad = scheduledLoad;
        this.priorities = priorities;
    }

    public static DashboardModel build(
            List<StudyTopic> topics,
            FsrsMemoryEngine engine,
            long now,
            ZoneId zoneId) {
        double targetRetention = engine.desiredRetention();
        int dueNow = 0;
        int missed = 0;
        int upcomingSevenDays = 0;
        int belowTarget = 0;
        int reviewedTopics = 0;
        int reviewedLastSevenDays = 0;
        int completedOnTime = 0;
        int completedLate = 0;
        int retrievalFailures = 0;
        double retrievabilitySum = 0.0;
        int[] scheduledLoad = new int[LOAD_DAYS];
        ArrayList<PriorityItem> candidates = new ArrayList<>();

        LocalDate today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate();
        long sevenDaysEnd =
                today.plusDays(7).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long sevenDaysAgo =
                today.minusDays(7).atStartOfDay(zoneId).toInstant().toEpochMilli();

        for (StudyTopic topic : topics) {
            double retrievability = engine.retrievability(topic, now);
            boolean hasReview = topic.lastReviewAt > 0;
            boolean effectiveMissed = isMissed(topic, now);
            boolean isDue = topic.nextReviewAt > 0 && topic.nextReviewAt <= now;

            if (hasReview) {
                reviewedTopics++;
                retrievabilitySum += retrievability;
                if (retrievability < targetRetention) {
                    belowTarget++;
                }
            }
            if (effectiveMissed) {
                missed++;
            }
            if (isDue) {
                dueNow++;
            } else if (topic.nextReviewAt > now && topic.nextReviewAt < sevenDaysEnd) {
                upcomingSevenDays++;
            }

            int bucket = loadBucket(topic.nextReviewAt, now, today, zoneId);
            if (bucket >= 0 && bucket < LOAD_DAYS) {
                scheduledLoad[bucket]++;
            }

            PriorityKind kind = null;
            if (effectiveMissed) {
                kind = PriorityKind.MISSED;
            } else if (isDue) {
                kind = PriorityKind.DUE;
            } else if (hasReview && retrievability < targetRetention) {
                kind = PriorityKind.BELOW_TARGET;
            } else if (topic.nextReviewAt > now && topic.nextReviewAt < sevenDaysEnd) {
                kind = PriorityKind.UPCOMING;
            }
            if (kind != null) {
                candidates.add(
                        new PriorityItem(
                                topic,
                                kind,
                                retrievability,
                                topic.nextReviewAt));
            }

            for (ReviewEvent event : topic.reviewEvents) {
                if (event.reviewedAt >= sevenDaysAgo && event.reviewedAt <= now) {
                    reviewedLastSevenDays++;
                }
                if (event.completionStatus == ReviewScheduleStatus.COMPLETED_ON_TIME) {
                    completedOnTime++;
                } else if (event.completionStatus
                        == ReviewScheduleStatus.COMPLETED_LATE) {
                    completedLate++;
                }
                if (event.rating == ReviewRating.AGAIN) {
                    retrievalFailures++;
                }
            }
        }

        candidates.sort(priorityComparator());
        List<PriorityItem> priorities =
                new ArrayList<>(
                        candidates.subList(0, Math.min(MAX_PRIORITIES, candidates.size())));
        double average = reviewedTopics == 0 ? 0.0 : retrievabilitySum / reviewedTopics;
        int completed = completedOnTime + completedLate;
        double punctuality = completed == 0 ? 0.0 : completedOnTime / (double) completed;

        return new DashboardModel(
                now,
                targetRetention,
                topics.size(),
                reviewedTopics,
                dueNow,
                missed,
                upcomingSevenDays,
                belowTarget,
                reviewedLastSevenDays,
                completedOnTime,
                completedLate,
                retrievalFailures,
                average,
                punctuality,
                scheduledLoad,
                priorities);
    }

    private static boolean isMissed(StudyTopic topic, long now) {
        return topic.scheduleStatus == ReviewScheduleStatus.MISSED
                || (topic.scheduleStatus == ReviewScheduleStatus.SCHEDULED
                        && topic.scheduleGraceDeadline > 0
                        && topic.scheduleGraceDeadline < now);
    }

    private static int loadBucket(
            long scheduledAt,
            long now,
            LocalDate today,
            ZoneId zoneId) {
        if (scheduledAt <= 0) {
            return -1;
        }
        if (scheduledAt <= now) {
            return 0;
        }
        LocalDate scheduledDate =
                Instant.ofEpochMilli(scheduledAt).atZone(zoneId).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, scheduledDate);
        return days >= 0 && days < LOAD_DAYS ? (int) days : -1;
    }

    private static Comparator<PriorityItem> priorityComparator() {
        return (left, right) -> {
            int rank = Integer.compare(rank(left.kind), rank(right.kind));
            if (rank != 0) {
                return rank;
            }
            if (left.kind == PriorityKind.BELOW_TARGET) {
                int memory = Double.compare(left.retrievability, right.retrievability);
                if (memory != 0) {
                    return memory;
                }
            }
            int date = Long.compare(left.scheduledAt, right.scheduledAt);
            if (date != 0) {
                return date;
            }
            return left.topic.name.compareToIgnoreCase(right.topic.name);
        };
    }

    private static int rank(PriorityKind kind) {
        return switch (kind) {
            case MISSED -> 0;
            case DUE -> 1;
            case BELOW_TARGET -> 2;
            case UPCOMING -> 3;
        };
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
