package com.curvadeolvido.app.dashboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.curvadeolvido.app.domain.FsrsMemoryEngine;
import com.curvadeolvido.app.domain.ReviewRating;
import com.curvadeolvido.app.domain.ReviewResult;
import com.curvadeolvido.app.domain.ReviewScheduleStatus;
import com.curvadeolvido.app.domain.StudyTopic;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class DashboardModelTest {
    private static final long DAY = 86_400_000L;
    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    @Test
    public void countsDueMissedAndUpcomingTopics() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine(0.90);
        long now = 1_720_000_000_000L;
        StudyTopic missed = reviewedTopic(engine, 1L, now - 3 * DAY);
        missed.nextReviewAt = now - DAY;
        missed.scheduleGraceDeadline = now - 12 * 3_600_000L;
        missed.scheduleStatus = ReviewScheduleStatus.MISSED;

        StudyTopic upcoming = reviewedTopic(engine, 2L, now - DAY);
        upcoming.nextReviewAt = now + 2 * DAY;
        upcoming.scheduleGraceDeadline = upcoming.nextReviewAt + DAY;

        DashboardModel model =
                DashboardModel.build(List.of(missed, upcoming), engine, now, ZONE);

        assertEquals(1, model.dueNow);
        assertEquals(1, model.missed);
        assertEquals(1, model.upcomingSevenDays);
        assertEquals(1, model.scheduledLoad[0]);
        assertEquals(1, model.scheduledLoad[2]);
    }

    @Test
    public void excludesUnreviewedTopicsFromAverageMemory() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine(0.90);
        long now = 1_720_000_000_000L;
        StudyTopic reviewed = reviewedTopic(engine, 3L, now - DAY);
        StudyTopic newTopic = engine.newTopic(4L, "Nuevo", "", "", "", now);

        DashboardModel model =
                DashboardModel.build(List.of(reviewed, newTopic), engine, now, ZONE);

        assertEquals(1, model.reviewedTopics);
        assertEquals(engine.retrievability(reviewed, now), model.averageRetrievability, 0.000001);
    }

    @Test
    public void missedTopicsLeadPriorityQueue() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine(0.90);
        long now = 1_720_000_000_000L;
        StudyTopic lowMemory = reviewedTopic(engine, 5L, now - 30 * DAY);
        lowMemory.nextReviewAt = now + 3 * DAY;

        StudyTopic missed = reviewedTopic(engine, 6L, now - 3 * DAY);
        missed.nextReviewAt = now - DAY;
        missed.scheduleGraceDeadline = now - 1;
        missed.scheduleStatus = ReviewScheduleStatus.MISSED;

        DashboardModel model =
                DashboardModel.build(List.of(lowMemory, missed), engine, now, ZONE);

        assertEquals(DashboardModel.PriorityKind.MISSED, model.priorities.get(0).kind);
        assertEquals(missed.id, model.priorities.get(0).topic.id);
    }

    @Test
    public void calculatesPunctualityFromCompletedReviewsOnly() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine(0.90);
        long now = 1_720_000_000_000L;
        StudyTopic topic = reviewedTopic(engine, 7L, now - 2 * DAY);
        ReviewResult second =
                engine.review(topic, ReviewRating.GOOD, now - DAY, 0, "Tema", "Preguntas", "", "");
        topic.reviewEvents.get(0).completionStatus = ReviewScheduleStatus.COMPLETED_LATE;
        topic.reviewEvents.get(1).completionStatus = ReviewScheduleStatus.COMPLETED_ON_TIME;

        DashboardModel model = DashboardModel.build(List.of(topic), engine, now, ZONE);

        assertEquals(1, model.completedOnTime);
        assertEquals(1, model.completedLate);
        assertEquals(0.50, model.punctualityRate, 0.000001);
        assertTrue(second.event.reviewedAt > 0);
    }

    @Test
    public void usesEngineTargetRetention() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine(0.95);
        long now = 1_720_000_000_000L;
        DashboardModel model =
                DashboardModel.build(new ArrayList<>(), engine, now, ZONE);
        assertEquals(0.95, model.targetRetention, 0.000001);
    }

    private static StudyTopic reviewedTopic(
            FsrsMemoryEngine engine, long id, long reviewedAt) {
        StudyTopic topic = engine.newTopic(id, "Tema " + id, "Medicina", "Renal", "", reviewedAt);
        engine.review(topic, ReviewRating.GOOD, reviewedAt, 10, "Tema", "Preguntas", "", "");
        return topic;
    }
}
