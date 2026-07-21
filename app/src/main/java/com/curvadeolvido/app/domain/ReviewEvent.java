package com.curvadeolvido.app.domain;

public final class ReviewEvent {
    public long id;
    public long topicId;
    public long scheduledReviewId;
    public long scheduledAt;
    public long reviewedAt;
    public ReviewScheduleStatus completionStatus;
    public ReviewRating rating;
    public boolean wasPreviouslyMissed;

    public double retrievabilityBefore;
    public double retrievabilityAfter;
    public double stabilityBefore;
    public double stabilityAfter;
    public double difficultyBefore;
    public double difficultyAfter;

    public int coverageAdded;
    public String studied = "";
    public String method = "";
    public String remaining = "";
    public String photoBase64 = "";
}
