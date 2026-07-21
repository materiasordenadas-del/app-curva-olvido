package com.curvadeolvido.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.curvadeolvido.app.data.StudyRepository;
import com.curvadeolvido.app.domain.FsrsMemoryEngine;
import com.curvadeolvido.app.domain.StudyTopic;
import java.util.List;

public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "study_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        FsrsMemoryEngine engine = new FsrsMemoryEngine();
        StudyRepository repository = new StudyRepository(context, engine);

        repository.loadDueTopics(
                System.currentTimeMillis(),
                dueTopics -> {
                    try {
                        if (!dueTopics.isEmpty()) {
                            showNotification(context, dueTopics);
                        }
                    } finally {
                        repository.close();
                        pendingResult.finish();
                    }
                },
                error -> {
                    repository.close();
                    pendingResult.finish();
                });
    }

    private static void showNotification(Context context, List<StudyTopic> dueTopics) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                    new NotificationChannel(
                            CHANNEL,
                            "Recordatorios de estudio",
                            NotificationManager.IMPORTANCE_DEFAULT));
        }

        int due = dueTopics.size();
        String first = dueTopics.get(0).name;
        String second = due > 1 ? dueTopics.get(1).name : "";
        String detail =
                due == 1
                        ? "Tienes 1 tema pendiente: " + first
                        : "Tienes "
                                + due
                                + " temas pendientes: "
                                + first
                                + (second.isEmpty() ? "" : " y " + second);

        android.app.Notification.Builder note =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? new android.app.Notification.Builder(context, CHANNEL)
                        : new android.app.Notification.Builder(context);

        note.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Hora de repasar")
                .setContentText(detail)
                .setAutoCancel(true);

        manager.notify(9001, note.build());
    }
}
