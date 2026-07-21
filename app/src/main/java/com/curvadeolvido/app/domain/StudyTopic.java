package com.curvadeolvido.app.domain;

import java.util.ArrayList;
import java.util.List;

public final class StudyTopic {
    public long id;
    public String name = "";
    public String subject = "";
    public String section = "";
    public String notes = "";

    /** Porcentaje del contenido cubierto. No se usa para calcular memoria ni intervalos. */
    public int coveragePercent;

    /** Estado serializado del algoritmo FSRS. */
    public String fsrsCardJson = "";

    public long createdAt;
    public long lastReviewAt;
    public long nextReviewAt;
    public int lapseCount;
    public long activeScheduleId;
    public long scheduleGraceDeadline;
    public ReviewScheduleStatus scheduleStatus = ReviewScheduleStatus.SCHEDULED;

    public final List<ReviewEvent> reviewEvents = new ArrayList<>();
    public final List<ScheduledReview> scheduledReviews = new ArrayList<>();
}
