package com.curvadeolvido.app.visualization;

import com.curvadeolvido.app.domain.FsrsMemoryEngine;
import com.curvadeolvido.app.domain.MemorySnapshot;
import com.curvadeolvido.app.domain.ReviewEvent;
import com.curvadeolvido.app.domain.ReviewRating;
import com.curvadeolvido.app.domain.ReviewScheduleStatus;
import com.curvadeolvido.app.domain.ScheduledReview;
import com.curvadeolvido.app.domain.StudyTopic;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure timeline model used by the Android Canvas and unit tests. */
public final class ForgettingCurveModel {
    public static final long HOUR = 3_600_000L;
    public static final long DAY = 86_400_000L;
    public static final double DEFAULT_TARGET_RETENTION = 0.90;
    private static final int MAX_VISIBLE_REVIEW_EVENTS = 12;
    private static final int MAX_VISIBLE_SCHEDULES = 12;
    // Java-FSRS 1.0 default forgetting curve: DECAY = -w20, w20 = 0.2.
    private static final double FSRS_DECAY = -0.2;
    private static final double FSRS_FACTOR = Math.pow(0.9, 1.0 / FSRS_DECAY) - 1.0;

    public enum MarkerKind {
        COMPLETED_ON_TIME,
        COMPLETED_LATE,
        MISSED,
        RETRIEVAL_FAILURE,
        UPCOMING
    }

    public static final class Window {
        public final long startAt;
        public final long endAt;
        public final long tickMillis;
        public final String scaleLabel;

        Window(long startAt, long endAt, long tickMillis, String scaleLabel) {
            this.startAt = startAt;
            this.endAt = endAt;
            this.tickMillis = tickMillis;
            this.scaleLabel = scaleLabel;
        }

        public long spanMillis() {
            return Math.max(1L, endAt - startAt);
        }
    }

    public static final class Segment {
        public final long startAt;
        public final long endAt;
        public final long anchorReviewAt;
        public final double stabilityDays;
        public final boolean projected;

        Segment(
                long startAt,
                long endAt,
                long anchorReviewAt,
                double stabilityDays,
                boolean projected) {
            this.startAt = startAt;
            this.endAt = endAt;
            this.anchorReviewAt = anchorReviewAt;
            this.stabilityDays = stabilityDays;
            this.projected = projected;
        }
    }

    public static final class Reset {
        public final long reviewedAt;
        public final double before;
        public final double after;

        Reset(long reviewedAt, double before, double after) {
            this.reviewedAt = reviewedAt;
            this.before = clamp01(before);
            this.after = clamp01(after);
        }
    }

    public static final class Marker {
        public final long timestamp;
        public final double retrievability;
        public final MarkerKind kind;
        public final String label;

        Marker(long timestamp, double retrievability, MarkerKind kind, String label) {
            this.timestamp = timestamp;
            this.retrievability = clamp01(retrievability);
            this.kind = kind;
            this.label = label;
        }
    }

    public final Window window;
    public final List<Segment> segments;
    public final List<Reset> resets;
    public final List<Marker> markers;
    public final double targetRetention;
    public final double currentRetrievability;
    public final long measuredAt;

    private ForgettingCurveModel(
            Window window,
            List<Segment> segments,
            List<Reset> resets,
            List<Marker> markers,
            double targetRetention,
            double currentRetrievability,
            long measuredAt) {
        this.window = window;
        this.segments = segments;
        this.resets = resets;
        this.markers = markers;
        this.targetRetention = targetRetention;
        this.currentRetrievability = currentRetrievability;
        this.measuredAt = measuredAt;
    }

    public static ForgettingCurveModel build(
            StudyTopic topic, FsrsMemoryEngine engine, long now) {
        return build(topic, engine, now, engine.desiredRetention());
    }

    public static ForgettingCurveModel build(
            StudyTopic topic,
            FsrsMemoryEngine engine,
            long now,
            double targetRetention) {
        List<ReviewEvent> events = meaningfulEvents(topic.reviewEvents);
        List<ScheduledReview> schedules = visibleSchedules(topic.scheduledReviews);
        Window window = adaptiveWindow(topic, events, schedules, now);
        List<Segment> segments = buildSegments(topic, events, engine, window, now);
        List<Reset> resets = buildResets(events);
        List<Marker> markers =
                buildMarkers(topic, events, schedules, segments, window, now);
        return new ForgettingCurveModel(
                window,
                segments,
                resets,
                markers,
                clamp01(targetRetention),
                engine.retrievability(topic, now),
                now);
    }

    public double retrievabilityAt(Segment segment, long timestamp) {
        return projectedRetrievability(
                segment.stabilityDays, segment.anchorReviewAt, timestamp);
    }

    private static List<ReviewEvent> meaningfulEvents(List<ReviewEvent> source) {
        ArrayList<ReviewEvent> events = new ArrayList<>();
        for (ReviewEvent event : source) {
            if (event.reviewedAt > 0 && event.stabilityAfter > 0) {
                events.add(event);
            }
        }
        events.sort(Comparator.comparingLong(item -> item.reviewedAt));
        if (events.size() > MAX_VISIBLE_REVIEW_EVENTS) {
            return new ArrayList<>(
                    events.subList(events.size() - MAX_VISIBLE_REVIEW_EVENTS, events.size()));
        }
        return events;
    }

    private static List<ScheduledReview> visibleSchedules(List<ScheduledReview> source) {
        ArrayList<ScheduledReview> schedules = new ArrayList<>(source);
        schedules.sort(Comparator.comparingLong(item -> item.scheduledAt));
        if (schedules.size() > MAX_VISIBLE_SCHEDULES) {
            return new ArrayList<>(
                    schedules.subList(
                            schedules.size() - MAX_VISIBLE_SCHEDULES, schedules.size()));
        }
        return schedules;
    }

    private static Window adaptiveWindow(
            StudyTopic topic,
            List<ReviewEvent> events,
            List<ScheduledReview> schedules,
            long now) {
        long earliest = topic.createdAt > 0 ? topic.createdAt : now;
        if (!events.isEmpty()) {
            earliest = events.get(0).reviewedAt;
        }
        for (ScheduledReview schedule : schedules) {
            if (schedule.scheduledAt > 0 && schedule.scheduledAt < earliest) {
                earliest = schedule.scheduledAt;
            }
        }

        long latest = Math.max(now, topic.nextReviewAt);
        for (ReviewEvent event : events) {
            latest = Math.max(latest, event.reviewedAt);
        }
        for (ScheduledReview schedule : schedules) {
            latest = Math.max(latest, schedule.scheduledAt);
            latest = Math.max(latest, schedule.completedAt);
            latest = Math.max(latest, schedule.missedAt);
        }

        long rawSpan = Math.max(HOUR, latest - earliest);
        long minimumSpan;
        if (rawSpan <= 12 * HOUR) {
            minimumSpan = 24 * HOUR;
        } else if (rawSpan <= 2 * DAY) {
            minimumSpan = 3 * DAY;
        } else if (rawSpan <= 21 * DAY) {
            minimumSpan = 28 * DAY;
        } else if (rawSpan <= 120 * DAY) {
            minimumSpan = 180 * DAY;
        } else {
            minimumSpan = rawSpan;
        }
        long span = Math.max(rawSpan, minimumSpan);
        long leftPadding = Math.max(HOUR, Math.round(span * 0.04));
        long rightPadding = Math.max(2 * HOUR, Math.round(span * 0.12));
        long start = Math.max(0, earliest - leftPadding);
        long end = Math.max(start + span, latest + rightPadding);

        long visibleSpan = end - start;
        long tick;
        String label;
        if (visibleSpan <= 2 * DAY) {
            tick = 6 * HOUR;
            label = "horas";
        } else if (visibleSpan <= 21 * DAY) {
            tick = 2 * DAY;
            label = "días";
        } else if (visibleSpan <= 120 * DAY) {
            tick = 14 * DAY;
            label = "semanas";
        } else if (visibleSpan <= 730 * DAY) {
            tick = 60 * DAY;
            label = "meses";
        } else {
            tick = 365 * DAY;
            label = "años";
        }
        return new Window(start, end, tick, label);
    }

    private static List<Segment> buildSegments(
            StudyTopic topic,
            List<ReviewEvent> events,
            FsrsMemoryEngine engine,
            Window window,
            long now) {
        ArrayList<Segment> result = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            ReviewEvent event = events.get(index);
            long end =
                    index + 1 < events.size()
                            ? events.get(index + 1).reviewedAt
                            : window.endAt;
            if (end > event.reviewedAt) {
                if (event.reviewedAt < now && end > now) {
                    result.add(
                            new Segment(
                                    event.reviewedAt,
                                    now,
                                    event.reviewedAt,
                                    event.stabilityAfter,
                                    false));
                    result.add(
                            new Segment(
                                    now,
                                    end,
                                    event.reviewedAt,
                                    event.stabilityAfter,
                                    true));
                } else {
                    result.add(
                            new Segment(
                                    event.reviewedAt,
                                    end,
                                    event.reviewedAt,
                                    event.stabilityAfter,
                                    event.reviewedAt >= now));
                }
            }
        }

        if (result.isEmpty() && topic.lastReviewAt > 0) {
            MemorySnapshot snapshot = engine.snapshot(topic, now);
            if (snapshot.stabilityDays > 0) {
                long end = Math.max(topic.lastReviewAt + HOUR, window.endAt);
                if (topic.lastReviewAt < now) {
                    result.add(
                            new Segment(
                                    topic.lastReviewAt,
                                    now,
                                    topic.lastReviewAt,
                                    snapshot.stabilityDays,
                                    false));
                    result.add(
                            new Segment(
                                    now,
                                    end,
                                    topic.lastReviewAt,
                                    snapshot.stabilityDays,
                                    true));
                } else {
                    result.add(
                            new Segment(
                                    topic.lastReviewAt,
                                    end,
                                    topic.lastReviewAt,
                                    snapshot.stabilityDays,
                                    true));
                }
            }
        }
        return result;
    }

    private static List<Reset> buildResets(List<ReviewEvent> events) {
        ArrayList<Reset> resets = new ArrayList<>();
        for (ReviewEvent event : events) {
            resets.add(
                    new Reset(
                            event.reviewedAt,
                            event.retrievabilityBefore,
                            event.retrievabilityAfter));
        }
        return resets;
    }

    private static List<Marker> buildMarkers(
            StudyTopic topic,
            List<ReviewEvent> events,
            List<ScheduledReview> schedules,
            List<Segment> segments,
            Window window,
            long now) {
        ArrayList<Marker> markers = new ArrayList<>();

        for (ReviewEvent event : events) {
            MarkerKind completionKind =
                    event.completionStatus == ReviewScheduleStatus.COMPLETED_LATE
                            ? MarkerKind.COMPLETED_LATE
                            : MarkerKind.COMPLETED_ON_TIME;
            String completionLabel =
                    completionKind == MarkerKind.COMPLETED_LATE
                            ? "Repaso completado tarde"
                            : "Repaso completado a tiempo";
            markers.add(
                    new Marker(
                            event.reviewedAt,
                            event.retrievabilityBefore,
                            completionKind,
                            completionLabel));
            if (event.rating == ReviewRating.AGAIN) {
                markers.add(
                        new Marker(
                                event.reviewedAt,
                                event.retrievabilityBefore,
                                MarkerKind.RETRIEVAL_FAILURE,
                                "Fallo de recuperación"));
            }
        }

        boolean activeScheduleMissed = false;
        for (ScheduledReview schedule : schedules) {
            boolean inferredMissed =
                    schedule.status == ReviewScheduleStatus.SCHEDULED
                            && schedule.graceDeadline > 0
                            && schedule.graceDeadline < now;
            if (schedule.status == ReviewScheduleStatus.MISSED
                    || inferredMissed
                    || schedule.missedAt > 0) {
                long timestamp =
                        schedule.missedAt > 0
                                ? schedule.missedAt
                                : Math.max(schedule.scheduledAt, schedule.graceDeadline);
                markers.add(
                        new Marker(
                                timestamp,
                                valueAt(segments, timestamp),
                                MarkerKind.MISSED,
                                "Repaso omitido"));
                if (schedule.id == topic.activeScheduleId
                        || schedule.scheduledAt == topic.nextReviewAt) {
                    activeScheduleMissed = true;
                }
            }
        }

        if (topic.nextReviewAt > 0
                && topic.nextReviewAt <= window.endAt
                && !activeScheduleMissed) {
            markers.add(
                    new Marker(
                            topic.nextReviewAt,
                            valueAt(segments, topic.nextReviewAt),
                            MarkerKind.UPCOMING,
                            topic.nextReviewAt <= now ? "Repaso pendiente" : "Próximo repaso"));
        }
        markers.sort(Comparator.comparingLong(item -> item.timestamp));
        return markers;
    }

    private static double valueAt(
            List<Segment> segments, long timestamp) {
        for (Segment segment : segments) {
            if (timestamp >= segment.startAt && timestamp <= segment.endAt) {
                return projectedRetrievability(
                        segment.stabilityDays, segment.anchorReviewAt, timestamp);
            }
        }
        return 0.0;
    }

    static double projectedRetrievability(
            double stabilityDays, long reviewedAt, long measuredAt) {
        if (stabilityDays <= 0.0 || measuredAt < reviewedAt) {
            return 0.0;
        }
        double elapsedDays = (measuredAt - reviewedAt) / (double) DAY;
        return clamp01(
                Math.pow(1.0 + FSRS_FACTOR * elapsedDays / stabilityDays, FSRS_DECAY));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
