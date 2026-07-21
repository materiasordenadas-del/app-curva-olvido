package com.curvadeolvido.app.domain;

public final class ReviewResult {
    public final StudyTopic topic;
    public final ReviewEvent event;
    public final ScheduledReview nextSchedule;

    public ReviewResult(StudyTopic topic, ReviewEvent event, ScheduledReview nextSchedule) {
        this.topic = topic;
        this.event = event;
        this.nextSchedule = nextSchedule;
    }
}
