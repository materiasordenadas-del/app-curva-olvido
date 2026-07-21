package com.curvadeolvido.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RetentionPersonalizationTest {
    @Test
    public void higherTargetSchedulesNoLaterForSameRecall() {
        long now = 1_720_000_000_000L;
        FsrsMemoryEngine lower = new FsrsMemoryEngine(0.85);
        FsrsMemoryEngine higher = new FsrsMemoryEngine(0.95);
        StudyTopic lowerTopic = lower.newTopic(1L, "Tema", "", "", "", now);
        StudyTopic higherTopic = higher.newTopic(2L, "Tema", "", "", "", now);

        lower.review(lowerTopic, ReviewRating.GOOD, now, 0, "Tema", "Preguntas", "", "");
        higher.review(higherTopic, ReviewRating.GOOD, now, 0, "Tema", "Preguntas", "", "");

        assertTrue(higherTopic.nextReviewAt <= lowerTopic.nextReviewAt);
    }

    @Test
    public void creatingAnotherEngineDoesNotRewritePersistedDate() {
        long now = 1_720_000_000_000L;
        FsrsMemoryEngine original = new FsrsMemoryEngine(0.90);
        StudyTopic topic = original.newTopic(3L, "Tema", "", "", "", now);
        original.review(topic, ReviewRating.GOOD, now, 0, "Tema", "Preguntas", "", "");
        long scheduled = topic.nextReviewAt;

        FsrsMemoryEngine changed = new FsrsMemoryEngine(0.95);

        assertEquals(0.95, changed.desiredRetention(), 0.000001);
        assertEquals(scheduled, topic.nextReviewAt);
    }
}
