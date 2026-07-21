package com.curvadeolvido.app.notifications;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.curvadeolvido.app.data.StudyDao;
import com.curvadeolvido.app.data.StudyDatabase;
import com.curvadeolvido.app.data.TopicEntity;
import java.util.List;

/** Loads due topics from Room and emits notifications. */
public final class ReminderWorker extends Worker {
    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean daily = getInputData().getBoolean(ReminderScheduler.INPUT_DAILY, false);
        long topicId = getInputData().getLong(ReminderScheduler.INPUT_TOPIC_ID, -1L);
        try {
            long now = System.currentTimeMillis();
            StudyDao dao = StudyDatabase.get(getApplicationContext()).studyDao();
            dao.markOverdueSchedulesMissed(now);

            if (topicId > 0) {
                TopicEntity topic = dao.findTopic(topicId);
                if (topic != null && topic.nextReviewAt > 0 && topic.nextReviewAt <= now) {
                    ReminderNotifications.showSingleTopic(getApplicationContext(), topic);
                } else {
                    ReminderNotifications.cancelTopic(getApplicationContext(), topicId);
                }
            } else {
                List<TopicEntity> due = dao.loadDueTopics(now);
                ReminderNotifications.showDueTopics(getApplicationContext(), due);
            }

            if (daily) {
                ReminderScheduler.onDailyRunCompleted(getApplicationContext());
            }
            return Result.success();
        } catch (Throwable error) {
            return Result.retry();
        }
    }
}
