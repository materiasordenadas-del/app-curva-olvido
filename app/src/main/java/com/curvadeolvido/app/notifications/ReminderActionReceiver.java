package com.curvadeolvido.app.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles notification actions without recording a cognitive review. */
public final class ReminderActionReceiver extends BroadcastReceiver {
    public static final String ACTION_SNOOZE =
            "com.curvadeolvido.app.action.SNOOZE_REVIEW";
    public static final String EXTRA_TOPIC_ID = "topic_id";
    public static final String EXTRA_SNOOZE_MODE = "snooze_mode";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_SNOOZE.equals(intent.getAction())) {
            return;
        }
        long topicId = intent.getLongExtra(EXTRA_TOPIC_ID, -1L);
        String mode = intent.getStringExtra(EXTRA_SNOOZE_MODE);
        ReminderScheduler.snoozeTopic(context, topicId, mode);
    }
}
