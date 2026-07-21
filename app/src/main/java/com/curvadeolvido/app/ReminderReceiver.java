package com.curvadeolvido.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.ArrayList;
import org.json.JSONArray;

public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "study_reminders";
    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Recordatorios de estudio", NotificationManager.IMPORTANCE_DEFAULT));
        int due = 0;
        ArrayList<String> names = new ArrayList<>();
        try {
            JSONArray topics = new JSONArray(context.getSharedPreferences("curva", Context.MODE_PRIVATE).getString("topics", "[]"));
            long now = System.currentTimeMillis();
            for (int index = 0; index < topics.length(); index++) {
                MainActivity.Topic topic = MainActivity.Topic.from(topics.getJSONObject(index));
                if (now >= MainActivity.nextTimeFor(topic)) { due++; if (names.size() < 2) names.add(topic.name); }
            }
        } catch (Exception ignored) { }
        if (due == 0) return;
        String detail = due == 1 ? "Tienes 1 tema pendiente: " + names.get(0) : "Tienes " + due + " temas pendientes hoy" + (names.isEmpty() ? "" : ": " + names.get(0) + (names.size() > 1 ? " y " + names.get(1) : ""));
        android.app.Notification.Builder note = android.os.Build.VERSION.SDK_INT >= 26
            ? new android.app.Notification.Builder(context, CHANNEL)
            : new android.app.Notification.Builder(context);
        note.setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Hora de repasar")
            .setContentText(detail)
            .setAutoCancel(true);
        manager.notify(9001, note.build());
    }
}
