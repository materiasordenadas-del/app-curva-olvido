package com.curvadeolvido.app.visualization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.curvadeolvido.app.domain.FsrsMemoryEngine;
import com.curvadeolvido.app.domain.ReviewRating;
import com.curvadeolvido.app.domain.ReviewResult;
import com.curvadeolvido.app.domain.ReviewScheduleStatus;
import com.curvadeolvido.app.domain.ScheduledReview;
import com.curvadeolvido.app.domain.StudyTopic;
import org.junit.Test;

public class ForgettingCurveModelTest {
    private static final long DAY = ForgettingCurveModel.DAY;

    @Test
    public void adaptiveWindowIncludesNowAndNextReview() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long now = 1_720_000_000_000L;
        StudyTopic topic = engine.newTopic(1L, "Tema", "Medicina", "Renal", "", now);
        topic.nextReviewAt = now + 8 * ForgettingCurveModel.HOUR;

        ForgettingCurveModel model = ForgettingCurveModel.build(topic, engine, now);

        assertTrue(model.window.startAt <= now);
        assertTrue(model.window.endAt >= topic.nextReviewAt);
        assertEquals("horas", model.window.scaleLabel);
    }

    @Test
    public void completedLateAndRetrievalFailureRemainDifferentMarkers() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long start = 1_720_000_000_000L;
        StudyTopic topic = engine.newTopic(2L, "Tema", "Medicina", "Renal", "", start);

        ReviewResult first =
                engine.review(
                        topic,
                        ReviewRating.GOOD,
                        start,
                        0,
                        "Tema",
                        "Preguntas",
                        "",
                        "");
        first.event.completionStatus = ReviewScheduleStatus.COMPLETED_LATE;

        engine.review(
                topic,
                ReviewRating.AGAIN,
                start + DAY,
                0,
                "Tema",
                "Preguntas",
                "",
                "");

        ForgettingCurveModel model =
                ForgettingCurveModel.build(topic, engine, start + DAY + 1_000L);

        assertTrue(hasMarker(model, ForgettingCurveModel.MarkerKind.COMPLETED_LATE));
        assertTrue(hasMarker(model, ForgettingCurveModel.MarkerKind.RETRIEVAL_FAILURE));
    }

    @Test
    public void missedScheduleIsShownWithoutCreatingRetrievalFailure() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long now = 1_720_000_000_000L;
        StudyTopic topic = engine.newTopic(3L, "Tema", "Medicina", "Renal", "", now - 3 * DAY);
        engine.review(
                topic,
                ReviewRating.GOOD,
                now - 3 * DAY,
                0,
                "Tema",
                "Preguntas",
                "",
                "");

        ScheduledReview missed = new ScheduledReview();
        missed.topicId = topic.id;
        missed.scheduledAt = now - DAY;
        missed.graceDeadline = now - 12 * ForgettingCurveModel.HOUR;
        missed.missedAt = now - 12 * ForgettingCurveModel.HOUR;
        missed.status = ReviewScheduleStatus.MISSED;
        topic.scheduledReviews.add(missed);

        ForgettingCurveModel model = ForgettingCurveModel.build(topic, engine, now);

        assertTrue(hasMarker(model, ForgettingCurveModel.MarkerKind.MISSED));
        assertTrue(!hasMarker(model, ForgettingCurveModel.MarkerKind.RETRIEVAL_FAILURE));
    }

    @Test
    public void projectedSegmentKeepsOriginalReviewAnchor() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long reviewedAt = 1_720_000_000_000L;
        long now = reviewedAt + 2 * DAY;
        StudyTopic topic = engine.newTopic(4L, "Tema", "Medicina", "Renal", "", reviewedAt);
        engine.review(
                topic,
                ReviewRating.GOOD,
                reviewedAt,
                0,
                "Tema",
                "Preguntas",
                "",
                "");

        ForgettingCurveModel model = ForgettingCurveModel.build(topic, engine, now);
        ForgettingCurveModel.Segment projected =
                model.segments.stream().filter(segment -> segment.projected).findFirst().orElseThrow();

        assertEquals(reviewedAt, projected.anchorReviewAt);
        assertTrue(model.retrievabilityAt(projected, now) < 1.0);
    }

    @Test
    public void modelUsesNinetyPercentTargetByDefault() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        long now = 1_720_000_000_000L;
        StudyTopic topic = engine.newTopic(5L, "Tema", "Medicina", "Renal", "", now);

        ForgettingCurveModel model = ForgettingCurveModel.build(topic, engine, now);

        assertEquals(0.90, model.targetRetention, 0.000001);
    }

    @Test
    public void modelUsesPersonalizedEngineTarget() {
        FsrsMemoryEngine engine = new FsrsMemoryEngine(0.95);
        long now = 1_720_000_000_000L;
        StudyTopic topic = engine.newTopic(6L, "Tema", "Medicina", "Renal", "", now);

        ForgettingCurveModel model = ForgettingCurveModel.build(topic, engine, now);

        assertEquals(0.95, model.targetRetention, 0.000001);
    }

    @Test
    public void stabilityRepresentsNinetyPercentRetrievability() {
        long reviewedAt = 1_720_000_000_000L;
        double stabilityDays = 12.0;

        double value =
                ForgettingCurveModel.projectedRetrievability(
                        stabilityDays,
                        reviewedAt,
                        reviewedAt + Math.round(stabilityDays * DAY));

        assertEquals(0.90, value, 0.000001);
    }

    private static boolean hasMarker(
            ForgettingCurveModel model, ForgettingCurveModel.MarkerKind kind) {
        return model.markers.stream().anyMatch(marker -> marker.kind == kind);
    }
}
