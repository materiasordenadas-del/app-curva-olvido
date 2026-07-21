package com.curvadeolvido.app.data;

import androidx.room.Embedded;
import androidx.room.Relation;
import java.util.List;

public class TopicAggregate {
    @Embedded public TopicEntity topic;

    @Relation(parentColumn = "id", entityColumn = "topicId")
    public List<ReviewEventEntity> reviewEvents;

    @Relation(parentColumn = "id", entityColumn = "topicId")
    public List<ScheduledReviewEntity> scheduledReviews;
}
