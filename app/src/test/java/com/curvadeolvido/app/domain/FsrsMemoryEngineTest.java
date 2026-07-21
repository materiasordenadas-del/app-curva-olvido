package com.curvadeolvido.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import org.junit.Test;

public class FsrsMemoryEngineTest {
    private static final long DAY = Duration.ofDays(1).toMillis();

    @Test
    public void newTopicStartsFullAndThenForgetsOverTime() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long now = System.currentTimeMillis();
        StudyTopic topic = engine.newTopic(99L, "Tema nuevo", "Medicina", "Renal", "", now);

        double immediately = engine.retrievability(topic, now);
        double later = engine.retrievability(topic, now + 30 * DAY);

        assertTrue(immediately >= 0.99);
        assertTrue(later < immediately);
    }

    @Test
    public void successfulReviewStartsNearFullRetrievability() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long now = System.currentTimeMillis();
        StudyTopic topic = engine.newTopic(1L, "Tema", "Medicina", "Renal", "", now);

        engine.review(topic, ReviewRating.GOOD, now, 20, "Tema", "Preguntas", "", "");

        double retrievability = engine.retrievability(topic, now);
        assertTrue(retrievability >= 0.99);
    }

    @Test
    public void retrievabilityFallsWithElapsedTime() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long now = System.currentTimeMillis();
        StudyTopic topic = engine.newTopic(2L, "Tema", "Medicina", "Renal", "", now);

        engine.review(topic, ReviewRating.GOOD, now, 20, "Tema", "Preguntas", "", "");

        double immediately = engine.retrievability(topic, now);
        double later = engine.retrievability(topic, now + 30 * DAY);

        assertTrue(later < immediately);
    }

    @Test
    public void coverageDoesNotControlMemorySchedule() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long now = System.currentTimeMillis();

        StudyTopic lowCoverage =
                engine.newTopic(3L, "Tema A", "Medicina", "Renal", "", now);
        StudyTopic highCoverage =
                engine.newTopic(4L, "Tema B", "Medicina", "Renal", "", now);

        engine.review(
                lowCoverage,
                ReviewRating.GOOD,
                now,
                10,
                "Tema",
                "Preguntas",
                "",
                "");
        engine.review(
                highCoverage,
                ReviewRating.GOOD,
                now,
                100,
                "Tema",
                "Preguntas",
                "",
                "");

        assertEquals(lowCoverage.nextReviewAt, highCoverage.nextReviewAt);
        assertEquals(
                engine.retrievability(lowCoverage, now + DAY),
                engine.retrievability(highCoverage, now + DAY),
                0.000001);
    }

    @Test
    public void failedRetrievalSchedulesNoLaterThanGoodRecall() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long now = System.currentTimeMillis();

        StudyTopic failed =
                engine.newTopic(5L, "Tema A", "Medicina", "Renal", "", now);
        StudyTopic good =
                engine.newTopic(6L, "Tema B", "Medicina", "Renal", "", now);

        engine.review(failed, ReviewRating.AGAIN, now, 0, "Tema", "Preguntas", "", "");
        engine.review(good, ReviewRating.GOOD, now, 0, "Tema", "Preguntas", "", "");

        assertTrue(failed.nextReviewAt <= good.nextReviewAt);
        assertEquals(1, failed.lapseCount);
        assertEquals(0, good.lapseCount);
    }

    @Test
    public void lateCompletionIsNotRetrievalFailure() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long now = System.currentTimeMillis();
        StudyTopic topic = engine.newTopic(7L, "Tema", "Medicina", "Renal", "", now);
        topic.nextReviewAt = now - 2 * DAY;
        topic.scheduleGraceDeadline = now - DAY;
        topic.scheduleStatus = ReviewScheduleStatus.MISSED;

        ReviewResult result =
                engine.review(
                        topic,
                        ReviewRating.GOOD,
                        now,
                        0,
                        "Tema",
                        "Preguntas",
                        "",
                        "");

        assertEquals(ReviewScheduleStatus.COMPLETED_LATE, result.event.completionStatus);
        assertEquals(ReviewRating.GOOD, result.event.rating);
        assertTrue(!result.event.rating.isRetrievalFailure());
    }
}
