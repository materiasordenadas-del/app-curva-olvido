package com.curvadeolvido.app.domain;

public final class ScheduledReview {
    public long id;
    public long topicId;
    public long scheduledAt;
    public long graceDeadline;
    public ReviewScheduleStatus status = ReviewScheduleStatus.SCHEDULED;
    public long missedAt;
    public long completedAt;
}
