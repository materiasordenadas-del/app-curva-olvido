package com.curvadeolvido.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.curvadeolvido.app.data.StudyRepository;
import com.curvadeolvido.app.domain.FsrsMemoryEngine;
import com.curvadeolvido.app.domain.MemorySnapshot;
import com.curvadeolvido.app.domain.ReviewEvent;
import com.curvadeolvido.app.domain.ReviewRating;
import com.curvadeolvido.app.domain.ReviewResult;
import com.curvadeolvido.app.domain.ReviewScheduleStatus;
import com.curvadeolvido.app.domain.ScheduledReview;
import com.curvadeolvido.app.domain.StudyTopic;
import com.curvadeolvido.app.visualization.ForgettingCurveView;
import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(55, 115, 220);

    private final ArrayList<StudyTopic> topics = new ArrayList<>();
    private final FsrsMemoryEngine memoryEngine = new FsrsMemoryEngine();

    private SharedPreferences prefs;
    private StudyRepository repository;
    private LinearLayout content;
    private int reminderHour = 19;
    private int reminderMinute = 0;
    private String pendingPhoto = "";
    private TextView photoStatus;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("curva", MODE_PRIVATE);
        reminderHour = prefs.getInt("hour", 19);
        reminderMinute = prefs.getInt("minute", 0);
        repository = new StudyRepository(this, memoryEngine);
        showLoading();

        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 22);
        }

        repository.initialize(
                prefs,
                loaded -> {
                    topics.clear();
                    topics.addAll(loaded);
                    showHome();
                    if (!prefs.getBoolean("configured", false)) {
                        chooseTime(true);
                    } else {
                        scheduleDailyReminder();
                    }
                },
                this::showDataError);
    }

    @Override
    protected void onDestroy() {
        if (repository != null) {
            repository.close();
        }
        super.onDestroy();
    }

    private void showLoading() {
        LinearLayout loading = new LinearLayout(this);
        loading.setGravity(Gravity.CENTER);
        loading.setBackgroundColor(Color.rgb(29, 29, 31));
        loading.addView(text("Cargando biblioteca…", 18, Color.WHITE));
        setContentView(loading);
    }

    private void showHome() {
        ScrollView scroll = new ScrollView(this);
        content = column(Color.rgb(29, 29, 31), 22, 28, 22, 40);
        scroll.addView(content);
        setContentView(scroll);

        content.addView(text("Biblioteca de estudio", 28, Color.WHITE));
        TextView subtitle =
                text(
                        "Cobertura, recuperación y cumplimiento se registran por separado",
                        15,
                        Color.rgb(198, 184, 245));
        subtitle.setPadding(0, 6, 0, 16);
        content.addView(subtitle);

        Button create = primaryButton("+ Nueva página");
        create.setOnClickListener(v -> addPage());
        content.addView(create);

        Button clock = darkButton("Hora de recordatorios");
        clock.setOnClickListener(v -> chooseTime(false));
        content.addView(clock);

        addPrioritySummary();
        addLibrary();

        if (topics.isEmpty()) {
            TextView empty =
                    text(
                            "Crea una categoría, una sección y tu primera página.",
                            16,
                            Color.LTGRAY);
            empty.setPadding(8, 24, 8, 0);
            content.addView(empty);
        }
    }

    private void addPrioritySummary() {
        if (topics.isEmpty()) {
            return;
        }
        TextView title =
                text("Menor probabilidad estimada de recuerdo", 19, Color.rgb(215, 205, 250));
        title.setPadding(4, 24, 4, 7);
        content.addView(title);

        ArrayList<StudyTopic> ordered = new ArrayList<>(topics);
        ordered.sort(Comparator.comparingDouble(this::currentMemory));
        for (int index = 0; index < Math.min(3, ordered.size()); index++) {
            StudyTopic topic = ordered.get(index);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(14, 10, 14, 10);
            row.setBackground(round(Color.rgb(42, 42, 46), 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, 2, 0, 3);
            row.setLayoutParams(params);

            TextView percent =
                    text(Math.round(currentMemory(topic)) + "%", 20, memoryColor(currentMemory(topic)));
            percent.setGravity(Gravity.CENTER);
            row.addView(percent, new LinearLayout.LayoutParams(64, 54));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(topic.name, 16, Color.WHITE));
            labels.addView(text(dueText(topic), 13, Color.LTGRAY));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(v -> showPage(topic));
            content.addView(row);
        }
    }

    private void addLibrary() {
        TreeMap<String, TreeMap<String, ArrayList<StudyTopic>>> library =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (StudyTopic topic : topics) {
            String category = topic.subject.isBlank() ? "Sin categoría" : topic.subject;
            String section = topic.section.isBlank() ? "General" : topic.section;
            library.computeIfAbsent(
                            category, ignored -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                    .computeIfAbsent(section, ignored -> new ArrayList<>())
                    .add(topic);
        }

        for (String category : library.keySet()) {
            TextView categoryTitle = text(category, 20, Color.WHITE);
            categoryTitle.setPadding(4, 22, 4, 8);
            content.addView(categoryTitle);
            for (String section : library.get(category).keySet()) {
                TextView sectionTitle = text(section, 16, Color.rgb(205, 205, 215));
                sectionTitle.setPadding(18, 6, 4, 4);
                content.addView(sectionTitle);
                for (StudyTopic topic : library.get(category).get(section)) {
                    content.addView(pageCard(topic));
                }
            }
        }
    }

    private View pageCard(StudyTopic topic) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(18, 14, 18, 14);
        card.setBackground(round(Color.rgb(46, 46, 50), 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(18, 2, 0, 4);
        card.setLayoutParams(params);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(topic.name, 18, Color.WHITE));
        labels.addView(
                text(
                        "Recuerdo "
                                + Math.round(currentMemory(topic))
                                + "% · Cobertura "
                                + topic.coveragePercent
                                + "% · "
                                + dueText(topic),
                        13,
                        Color.LTGRAY));
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        card.setOnClickListener(v -> showPage(topic));
        return card;
    }

    private void showPage(StudyTopic topic) {
        ScrollView scroll = new ScrollView(this);
        content = column(Color.rgb(247, 249, 253), 28, 28, 28, 45);
        scroll.addView(content);
        setContentView(scroll);

        Button back = darkButton("< Volver a la biblioteca");
        back.setOnClickListener(v -> showHome());
        content.addView(back);

        content.addView(text(topic.subject + "  >  " + topic.section, 15, BLUE));
        TextView title = text(topic.name, 28, Color.rgb(23, 32, 51));
        title.setPadding(0, 10, 0, 10);
        content.addView(title);

        ForgettingCurveView curve = new ForgettingCurveView(this, topic, memoryEngine);
        content.addView(curve, new LinearLayout.LayoutParams(-1, dp(480)));
        TextView curveHelp =
                text(
                        "Toca un marcador para ver su fecha y el recuerdo estimado en ese punto.",
                        13,
                        Color.DKGRAY);
        curveHelp.setPadding(2, 6, 2, 10);
        content.addView(curveHelp);

        MemorySnapshot snapshot = memoryEngine.snapshot(topic, System.currentTimeMillis());
        content.addView(
                text(
                        "Cobertura: "
                                + topic.coveragePercent
                                + "% · Recuerdo estimado: "
                                + Math.round(snapshot.retrievability * 100)
                                + "%",
                        17,
                        BLUE));
        content.addView(
                text(
                        "Estabilidad: "
                                + oneDecimal(snapshot.stabilityDays)
                                + " días · Dificultad: "
                                + oneDecimal(snapshot.difficulty)
                                + " · Lapsos: "
                                + topic.lapseCount,
                        15,
                        Color.DKGRAY));
        content.addView(text("Próximo repaso: " + dueText(topic), 16, Color.DKGRAY));

        EditText editor = new EditText(this);
        editor.setText(topic.notes);
        editor.setTextSize(17);
        editor.setTextColor(Color.rgb(23, 32, 51));
        editor.setGravity(Gravity.TOP | Gravity.LEFT);
        editor.setMinLines(8);
        editor.setHint("Escribe tus apuntes");
        editor.setPadding(20, 20, 20, 20);
        editor.setBackground(round(Color.rgb(232, 243, 255), 16));
        content.addView(editor);

        Button save = primaryButton("Guardar página");
        save.setOnClickListener(
                v -> {
                    topic.notes = editor.getText().toString();
                    repository.updateTopic(
                            topic,
                            () -> Toast.makeText(this, "Página guardada", Toast.LENGTH_SHORT).show(),
                            this::showDataError);
                });
        content.addView(save);

        Button register = primaryButton("Registrar repaso");
        register.setOnClickListener(v -> logStudy(topic));
        content.addView(register);

        addScheduleHistory(topic);
        addReviewHistory(topic);
    }

    private void addScheduleHistory(StudyTopic topic) {
        content.addView(text("Programación", 21, Color.rgb(23, 32, 51)));
        content.addView(
                text(
                        "Estado actual: "
                                + statusLabel(effectiveStatus(topic))
                                + " · "
                                + formatDateTime(topic.nextReviewAt),
                        15,
                        Color.DKGRAY));

        ArrayList<ScheduledReview> history = new ArrayList<>(topic.scheduledReviews);
        history.sort(
                Comparator.comparingLong((ScheduledReview item) -> item.scheduledAt).reversed());
        for (int index = 0; index < Math.min(8, history.size()); index++) {
            ScheduledReview review = history.get(index);
            content.addView(
                    text(
                            "• "
                                    + formatDateTime(review.scheduledAt)
                                    + " — "
                                    + statusLabel(effectiveStatus(review)),
                            14,
                            Color.DKGRAY));
        }
    }

    private void addReviewHistory(StudyTopic topic) {
        content.addView(text("Historial de recuperación", 21, Color.rgb(23, 32, 51)));
        if (topic.reviewEvents.isEmpty()) {
            content.addView(text("Aún no hay repasos registrados.", 15, Color.DKGRAY));
            return;
        }

        for (ReviewEvent event : topic.reviewEvents) {
            String rating = event.rating == null ? "Registro migrado" : event.rating.labelEs();
            String line =
                    "• "
                            + formatDateTime(event.reviewedAt)
                            + " · "
                            + rating
                            + " · "
                            + statusLabel(event.completionStatus)
                            + " · "
                            + Math.round(event.retrievabilityBefore * 100)
                            + "% → "
                            + Math.round(event.retrievabilityAfter * 100)
                            + "%"
                            + (event.coverageAdded > 0
                                    ? " · Cobertura +" + event.coverageAdded + "%"
                                    : "");
            content.addView(text(line, 15, Color.DKGRAY));
            addPhoto(event.photoBase64);
        }
    }

    private void addPhoto(String photoBase64) {
        if (photoBase64 == null || photoBase64.isEmpty()) {
            return;
        }
        try {
            byte[] raw = Base64.decode(photoBase64, Base64.DEFAULT);
            ImageView photo = new ImageView(this);
            photo.setImageBitmap(BitmapFactory.decodeByteArray(raw, 0, raw.length));
            photo.setAdjustViewBounds(true);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, 320);
            size.setMargins(0, 8, 0, 12);
            content.addView(photo, size);
        } catch (Exception ignored) {
            // Un adjunto inválido no debe ocultar el evento.
        }
    }

    private void addPage() {
        LinearLayout form = form();
        EditText category = field("Categoría (ej.: Medicina)");
        EditText section = field("Sección (ej.: Nefrología)");
        EditText title = field("Título de la página");
        EditText notes = field("Qué estudiaste");
        notes.setMinLines(3);
        form.addView(category);
        form.addView(section);
        form.addView(title);
        form.addView(notes);

        new AlertDialog.Builder(this)
                .setTitle("Nueva página")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                        "Guardar",
                        (dialog, which) -> {
                            if (title.getText().toString().trim().isEmpty()) {
                                return;
                            }
                            long id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
                            StudyTopic topic =
                                    memoryEngine.newTopic(
                                            id,
                                            title.getText().toString(),
                                            category.getText().toString(),
                                            section.getText().toString(),
                                            notes.getText().toString(),
                                            System.currentTimeMillis());
                            repository.createTopic(
                                    topic,
                                    () -> {
                                        topics.add(topic);
                                        scheduleDailyReminder();
                                        showHome();
                                    },
                                    this::showDataError);
                        })
                .show();
    }

    private void logStudy(StudyTopic topic) {
        pendingPhoto = "";
        LinearLayout form = form();
        EditText coverage = field("Cobertura añadida hoy, 0–100 (opcional)");
        coverage.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText studied = field("Qué intentaste recuperar");
        EditText method = field("Método: preguntas, tarjetas, casos...");
        EditText remaining = field("Qué falta por estudiar (opcional)");

        Spinner recall = new Spinner(this);
        recall.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        new String[] {
                            "No lo recordé",
                            "Lo recordé con mucha dificultad",
                            "Lo recordé bien",
                            "Lo recordé con facilidad"
                        }));
        recall.setSelection(2);

        Button camera = new Button(this);
        camera.setText("Tomar foto de lo estudiado");
        camera.setOnClickListener(
                v -> {
                    try {
                        startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), 501);
                    } catch (Exception error) {
                        Toast.makeText(this, "No hay cámara disponible.", Toast.LENGTH_SHORT).show();
                    }
                });
        photoStatus = text("Sin foto adjunta", 14, Color.DKGRAY);

        form.addView(coverage);
        form.addView(studied);
        form.addView(method);
        form.addView(recall);
        form.addView(camera);
        form.addView(photoStatus);
        form.addView(remaining);

        new AlertDialog.Builder(this)
                .setTitle("Registrar repaso")
                .setMessage(
                        "La cobertura describe cuánto estudiaste. La recuperación controla el intervalo.")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                        "Guardar",
                        (dialog, which) -> {
                            String studiedValue = studied.getText().toString().trim();
                            String methodValue = method.getText().toString().trim();
                            if (studiedValue.isEmpty() || methodValue.isEmpty()) {
                                Toast.makeText(
                                                this,
                                                "Indica qué recuperaste y cómo lo evaluaste.",
                                                Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }

                            ReviewResult result =
                                    memoryEngine.review(
                                            topic,
                                            ReviewRating.fromSpinnerPosition(
                                                    recall.getSelectedItemPosition()),
                                            System.currentTimeMillis(),
                                            parseCoverage(coverage.getText().toString()),
                                            studiedValue,
                                            methodValue,
                                            remaining.getText().toString(),
                                            pendingPhoto);
                            repository.recordReview(
                                    result.topic,
                                    result.event,
                                    result.nextSchedule,
                                    () -> {
                                        scheduleDailyReminder();
                                        showPage(topic);
                                    },
                                    this::showDataError);
                        })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 501
                || resultCode != RESULT_OK
                || data == null
                || data.getExtras() == null) {
            return;
        }
        try {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.JPEG, 75, stream);
            pendingPhoto = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);
            if (photoStatus != null) {
                photoStatus.setText("Foto adjunta al registro");
            }
        } catch (Exception error) {
            Toast.makeText(this, "No se pudo guardar la foto.", Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseTime(boolean first) {
        TimePickerDialog dialog =
                new TimePickerDialog(
                        this,
                        (view, hour, minute) -> {
                            reminderHour = hour;
                            reminderMinute = minute;
                            prefs.edit()
                                    .putInt("hour", hour)
                                    .putInt("minute", minute)
                                    .putBoolean("configured", true)
                                    .apply();
                            scheduleDailyReminder();
                        },
                        reminderHour,
                        reminderMinute,
                        true);
        dialog.setTitle(first ? "¿A qué hora quieres estudiar?" : "Hora de recordatorios");
        dialog.show();
    }

    private void scheduleDailyReminder() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, reminderHour);
        calendar.set(java.util.Calendar.MINUTE, reminderMinute);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        9001,
                        new Intent(this, ReminderReceiver.class).putExtra("daily", true),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ((AlarmManager) getSystemService(ALARM_SERVICE))
                .setInexactRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent);
    }

    private double currentMemory(StudyTopic topic) {
        return memoryEngine.retrievability(topic, System.currentTimeMillis()) * 100.0;
    }

    private String dueText(StudyTopic topic) {
        ReviewScheduleStatus status = effectiveStatus(topic);
        if (memoryEngine.isDue(topic, System.currentTimeMillis())) {
            return status == ReviewScheduleStatus.MISSED
                    ? "repaso omitido y pendiente"
                    : "pendiente ahora";
        }
        return formatDateTime(topic.nextReviewAt);
    }

    private ReviewScheduleStatus effectiveStatus(StudyTopic topic) {
        if (topic.scheduleStatus == ReviewScheduleStatus.SCHEDULED
                && topic.scheduleGraceDeadline > 0
                && System.currentTimeMillis() > topic.scheduleGraceDeadline) {
            return ReviewScheduleStatus.MISSED;
        }
        return topic.scheduleStatus;
    }

    private ReviewScheduleStatus effectiveStatus(ScheduledReview review) {
        if (review.status == ReviewScheduleStatus.SCHEDULED
                && review.graceDeadline > 0
                && System.currentTimeMillis() > review.graceDeadline) {
            return ReviewScheduleStatus.MISSED;
        }
        return review.status;
    }

    private void showDataError(Throwable error) {
        Toast.makeText(
                        this,
                        "No se pudieron guardar los datos: "
                                + error.getClass().getSimpleName(),
                        Toast.LENGTH_LONG)
                .show();
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(35, 0, 35, 0);
        return form;
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        return field;
    }

    private LinearLayout column(int color, int left, int top, int right, int bottom) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(left, top, right, bottom);
        column.setBackgroundColor(color);
        return column;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(round(BLUE, 16));
        return button;
    }

    private Button darkButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(round(Color.rgb(58, 58, 62), 14));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private int memoryColor(double memoryPercent) {
        if (memoryPercent < 50) {
            return Color.rgb(237, 101, 135);
        }
        if (memoryPercent < 90) {
            return Color.rgb(246, 201, 84);
        }
        return Color.rgb(102, 204, 169);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int parseCoverage(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Math.min(100, Math.max(0, Integer.parseInt(value.trim())));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String formatDateTime(long timestamp) {
        if (timestamp <= 0) {
            return "sin fecha";
        }
        return DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                .format(new Date(timestamp));
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private static String statusLabel(ReviewScheduleStatus status) {
        if (status == null) {
            return "sin estado";
        }
        return switch (status) {
            case SCHEDULED -> "programado";
            case MISSED -> "omitido";
            case COMPLETED_ON_TIME -> "completado a tiempo";
            case COMPLETED_LATE -> "completado tarde";
            case MIGRATED -> "registro migrado";
        };
    }
}
