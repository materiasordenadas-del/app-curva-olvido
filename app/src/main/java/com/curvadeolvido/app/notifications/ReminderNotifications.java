package com.curvadeolvido.app.notifications;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import com.curvadeolvido.app.MainActivity;
import com.curvadeolvido.app.data.TopicEntity;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Notification presentation, deep links, grouping, and action PendingIntents. */
public final class ReminderNotifications {
    private static final String CHANNEL_ID = "study_reminders_v2";
    private static final String GROUP_KEY = "due_study_topics";
    private static final int SUMMARY_ID = 9001;
    private static final int MAX_INDIVIDUAL = 6;
    private static final String PREF_SHOWN_IDS = "shown_notification_topic_ids";

    private ReminderNotifications() {}

    public static boolean notificationsEnabled(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return manager.areNotificationsEnabled();
    }

    public static void showDueTopics(Context context, List<TopicEntity> dueTopics) {
        cancelPreviouslyShown(context);
        if (dueTopics == null || dueTopics.isEmpty() || !notificationsEnabled(context)) {
            cancelSummary(context);
            return;
        }
        createChannel(context);
        int visible = Math.min(MAX_INDIVIDUAL, dueTopics.size());
        Set<Long> shown = new LinkedHashSet<>();
        for (int index = 0; index < visible; index++) {
            TopicEntity topic = dueTopics.get(index);
            showTopicNotification(context, topic, dueTopics.size());
            shown.add(topic.id);
        }
        saveShownIds(context, shown);
        if (dueTopics.size() > 1) {
            showSummary(context, dueTopics);
        }
    }

    public static void showSingleTopic(Context context, TopicEntity topic) {
        if (topic == null || !notificationsEnabled(context)) {
            return;
        }
        createChannel(context);
        showTopicNotification(context, topic, 1);
        Set<Long> shown = loadShownIds(context);
        shown.add(topic.id);
        saveShownIds(context, shown);
    }

    public static void cancelTopic(Context context, long topicId) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(notificationId(topicId));
        Set<Long> shown = loadShownIds(context);
        shown.remove(topicId);
        saveShownIds(context, shown);
        if (shown.size() < 2) {
            manager.cancel(SUMMARY_ID);
        }
    }

    private static void showTopicNotification(
            Context context, TopicEntity topic, int total) {
        String dueText =
                topic.nextReviewAt > 0
                        ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(new Date(topic.nextReviewAt))
                        : "ahora";
        String body =
                "Pendiente desde "
                        + dueText
                        + ". Intenta recuperarlo antes de consultar tus apuntes.";

        Notification.Builder builder =
                builder(context)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle(topic.name)
                        .setContentText(body)
                        .setStyle(new Notification.BigTextStyle().bigText(body))
                        .setContentIntent(openTopicIntent(context, topic.id))
                        .setAutoCancel(true)
                        .setCategory(Notification.CATEGORY_REMINDER)
                        .setGroup(GROUP_KEY)
                        .setOnlyAlertOnce(true)
                        .setNumber(total)
                        .addAction(
                                snoozeAction(
                                        context,
                                        topic.id,
                                        ReminderScheduler.SNOOZE_30,
                                        "30 min",
                                        1))
                        .addAction(
                                snoozeAction(
                                        context,
                                        topic.id,
                                        ReminderScheduler.SNOOZE_120,
                                        "2 h",
                                        2))
                        .addAction(
                                snoozeAction(
                                        context,
                                        topic.id,
                                        ReminderScheduler.SNOOZE_TOMORROW,
                                        "Mañana",
                                        3));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(notificationId(topic.id), builder.build());
    }

    private static void showSummary(Context context, List<TopicEntity> topics) {
        Notification.InboxStyle style = new Notification.InboxStyle();
        int visible = Math.min(MAX_INDIVIDUAL, topics.size());
        for (int index = 0; index < visible; index++) {
            style.addLine(topics.get(index).name);
        }
        if (topics.size() > visible) {
            style.addLine("y " + (topics.size() - visible) + " temas más");
        }
        style.setSummaryText(topics.size() + " repasos pendientes");

        Intent open =
                new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent =
                PendingIntent.getActivity(
                        context,
                        SUMMARY_ID,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification summary =
                builder(context)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle("Repasos pendientes")
                        .setContentText(topics.size() + " temas requieren recuperación")
                        .setStyle(style)
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true)
                        .setGroup(GROUP_KEY)
                        .setGroupSummary(true)
                        .build();
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(SUMMARY_ID, summary);
    }

    private static Notification.Action snoozeAction(
            Context context, long topicId, String mode, String label, int actionIndex) {
        Intent intent =
                new Intent(context, ReminderActionReceiver.class)
                        .setAction(ReminderActionReceiver.ACTION_SNOOZE)
                        .setData(Uri.parse("curva://snooze/" + topicId + "/" + mode))
                        .putExtra(ReminderActionReceiver.EXTRA_TOPIC_ID, topicId)
                        .putExtra(ReminderActionReceiver.EXTRA_SNOOZE_MODE, mode);
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        actionRequestCode(topicId, actionIndex),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, label, pendingIntent)
                .build();
    }

    private static PendingIntent openTopicIntent(Context context, long topicId) {
        Intent intent =
                new Intent(context, MainActivity.class)
                        .setAction("open-topic-" + topicId)
                        .putExtra(MainActivity.EXTRA_TOPIC_ID, topicId)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                notificationId(topicId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Notification.Builder builder(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "Recordatorios de repaso",
                        NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Temas cuya recuperación está programada o vencida");
        manager.createNotificationChannel(channel);
    }

    private static int notificationId(long topicId) {
        int folded = (int) (topicId ^ (topicId >>> 32));
        return 10_000 + (folded & 0x3fffffff);
    }

    private static int actionRequestCode(long topicId, int actionIndex) {
        int folded = (int) (topicId ^ (topicId >>> 32));
        return 31 * folded + actionIndex;
    }

    private static void cancelPreviouslyShown(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        for (long id : loadShownIds(context)) {
            manager.cancel(notificationId(id));
        }
        saveShownIds(context, new LinkedHashSet<>());
    }

    private static void cancelSummary(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(SUMMARY_ID);
    }

    private static Set<Long> loadShownIds(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(PREF_SHOWN_IDS, "");
        Set<Long> ids = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) {
            return ids;
        }
        for (String part : raw.split(",")) {
            try {
                ids.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {
                // Ignore stale/corrupt identifiers.
            }
        }
        return ids;
    }

    private static void saveShownIds(Context context, Set<Long> ids) {
        StringBuilder raw = new StringBuilder();
        for (long id : ids) {
            if (raw.length() > 0) {
                raw.append(',');
            }
            raw.append(id);
        }
        context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_SHOWN_IDS, raw.toString())
                .apply();
    }
}
