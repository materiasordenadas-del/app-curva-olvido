package com.curvadeolvido.app.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/** Schedules persistent WorkManager jobs without changing the FSRS due date. */
public final class ReminderScheduler {
    public static final String PREFS = "curva";
    public static final String KEY_CONFIGURED = "configured";
    public static final String KEY_HOUR = "hour";
    public static final String KEY_MINUTE = "minute";
    private static final String KEY_DAILY_TARGET = "daily_work_target";

    public static final String INPUT_TOPIC_ID = "topic_id";
    public static final String INPUT_DAILY = "daily";

    public static final String SNOOZE_30 = "snooze_30";
    public static final String SNOOZE_120 = "snooze_120";
    public static final String SNOOZE_TOMORROW = "snooze_tomorrow";

    private static final String TAG_DAILY = "daily-study-reminder";
    private static final String UNIQUE_DAILY_PREFIX = "daily-study-reminder-";
    private static final String UNIQUE_SNOOZE_PREFIX = "topic-snooze-";

    private ReminderScheduler() {}

    public static void configureDaily(Context context, int hour, int minute) {
        cancelLegacyAlarm(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_CONFIGURED, true)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .putLong(KEY_DAILY_TARGET, 0L)
                .apply();
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_DAILY);
        enqueueNextDaily(context, true);
    }

    public static void ensureDaily(Context context) {
        cancelLegacyAlarm(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_CONFIGURED, false)) {
            return;
        }
        long now = System.currentTimeMillis();
        long recordedTarget = prefs.getLong(KEY_DAILY_TARGET, 0L);
        if (recordedTarget > now) {
            return;
        }
        enqueueNextDaily(context, false);
    }

    static void onDailyRunCompleted(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_DAILY_TARGET, 0L)
                .apply();
        enqueueNextDaily(context, false);
    }

    public static void snoozeTopic(Context context, long topicId, String mode) {
        if (topicId <= 0) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long delay;
        if (SNOOZE_120.equals(mode)) {
            delay = ReminderTiming.delayMinutes(120);
        } else if (SNOOZE_TOMORROW.equals(mode)) {
            int hour = prefs.getInt(KEY_HOUR, 19);
            int minute = prefs.getInt(KEY_MINUTE, 0);
            long target =
                    ReminderTiming.tomorrowAtMillis(now, hour, minute, ZoneId.systemDefault());
            delay = ReminderTiming.delayUntil(now, target);
        } else {
            delay = ReminderTiming.delayMinutes(30);
        }

        Data data = new Data.Builder().putLong(INPUT_TOPIC_ID, topicId).build();
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(ReminderWorker.class)
                        .setInputData(data)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        UNIQUE_SNOOZE_PREFIX + topicId,
                        ExistingWorkPolicy.REPLACE,
                        request);
        ReminderNotifications.cancelTopic(context, topicId);
    }

    public static void cancelTopicSnooze(Context context, long topicId) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_SNOOZE_PREFIX + topicId);
        ReminderNotifications.cancelTopic(context, topicId);
    }

    private static void cancelLegacyAlarm(Context context) {
        Intent legacy =
                new Intent()
                        .setClassName(context, "com.curvadeolvido.app.ReminderReceiver")
                        .putExtra("daily", true);
        PendingIntent pending =
                PendingIntent.getBroadcast(
                        context,
                        9001,
                        legacy,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending == null) {
            return;
        }
        AlarmManager manager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        manager.cancel(pending);
        pending.cancel();
    }

    private static void enqueueNextDaily(Context context, boolean replace) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int hour = prefs.getInt(KEY_HOUR, 19);
        int minute = prefs.getInt(KEY_MINUTE, 0);
        long now = System.currentTimeMillis();
        long target =
                ReminderTiming.nextDailyAtMillis(now, hour, minute, ZoneId.systemDefault());
        long delay = ReminderTiming.delayUntil(now, target);

        Data data = new Data.Builder().putBoolean(INPUT_DAILY, true).build();
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(ReminderWorker.class)
                        .setInputData(data)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .addTag(TAG_DAILY)
                        .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        UNIQUE_DAILY_PREFIX + target,
                        replace ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP,
                        request);
        prefs.edit().putLong(KEY_DAILY_TARGET, target).apply();
    }
}
