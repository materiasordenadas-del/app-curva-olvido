package com.curvadeolvido.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
import com.curvadeolvido.app.dashboard.DashboardModel;
import com.curvadeolvido.app.dashboard.ReviewLoadView;
import com.curvadeolvido.app.data.StudyRepository;
import com.curvadeolvido.app.domain.FsrsMemoryEngine;
import com.curvadeolvido.app.domain.MemorySnapshot;
import com.curvadeolvido.app.domain.ReviewEvent;
import com.curvadeolvido.app.domain.ReviewRating;
import com.curvadeolvido.app.domain.ReviewResult;
import com.curvadeolvido.app.domain.ReviewScheduleStatus;
import com.curvadeolvido.app.domain.ScheduledReview;
import com.curvadeolvido.app.domain.StudyTopic;
import com.curvadeolvido.app.notifications.ReminderNotifications;
import com.curvadeolvido.app.notifications.ReminderScheduler;
import com.curvadeolvido.app.settings.StudyPreferences;
import com.curvadeolvido.app.visualization.ForgettingCurveView;
import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;

public class MainActivity extends Activity {
    public static final String EXTRA_TOPIC_ID = "open_topic_id";

    private static final int BLUE = Color.rgb(55, 115, 220);
    private static final int REQUEST_NOTIFICATIONS = 22;

    private final ArrayList<StudyTopic> topics = new ArrayList<>();

    private FsrsMemoryEngine memoryEngine;
    private SharedPreferences prefs;
    private StudyRepository repository;
    private LinearLayout content;
    private int reminderHour = 19;
    private int reminderMinute = 0;
    private String pendingPhoto = "";
    private TextView photoStatus;
    private long pendingTopicId = -1L;
    private boolean dataLoaded;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(ReminderScheduler.PREFS, MODE_PRIVATE);
        reminderHour = prefs.getInt(ReminderScheduler.KEY_HOUR, 19);
        reminderMinute = prefs.getInt(ReminderScheduler.KEY_MINUTE, 0);
        memoryEngine =
                new FsrsMemoryEngine(StudyPreferences.readDesiredRetention(prefs));
        pendingTopicId = readRequestedTopic(getIntent());
        repository = new StudyRepository(this, memoryEngine);
        showLoading();

        repository.initialize(
                prefs,
                loaded -> {
                    topics.clear();
                    topics.addAll(loaded);
                    dataLoaded = true;
                    if (!prefs.getBoolean(ReminderScheduler.KEY_CONFIGURED, false)) {
                        showHome();
                        chooseTime(true);
                    } else {
                        ReminderScheduler.ensureDaily(this);
                        navigateAfterLoad();
                    }
                },
                this::showDataError);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        long requested = readRequestedTopic(intent);
        if (requested > 0) {
            pendingTopicId = requested;
        }
        if (dataLoaded) {
            navigateAfterLoad();
        }
    }

    @Override
    protected void onDestroy() {
        if (repository != null) {
            repository.close();
        }
        super.onDestroy();
    }

    private long readRequestedTopic(Intent intent) {
        return intent == null ? -1L : intent.getLongExtra(EXTRA_TOPIC_ID, -1L);
    }

    private void navigateAfterLoad() {
        if (pendingTopicId > 0) {
            StudyTopic topic = findTopic(pendingTopicId);
            pendingTopicId = -1L;
            if (topic != null) {
                ReminderNotifications.cancelTopic(this, topic.id);
                showPage(topic);
                return;
            }
        }
        showHome();
    }

    private StudyTopic findTopic(long topicId) {
        for (StudyTopic topic : topics) {
            if (topic.id == topicId) {
                return topic;
            }
        }
        return null;
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

        content.addView(text("Panel de estudio", 28, Color.WHITE));
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

        Button clock =
                darkButton(
                        String.format(
                                Locale.getDefault(),
                                "Hora diaria: %02d:%02d",
                                reminderHour,
                                reminderMinute));
        clock.setOnClickListener(v -> chooseTime(false));
        content.addView(clock);

        Button retention =
                darkButton(
                        "Objetivo de recuerdo: "
                                + StudyPreferences.percentageLabel(
                                        memoryEngine.desiredRetention()));
        retention.setOnClickListener(v -> chooseRetention());
        content.addView(retention);

        boolean enabled = ReminderNotifications.notificationsEnabled(this);
        Button notifications =
                darkButton(enabled ? "Notificaciones activas" : "Activar notificaciones");
        notifications.setOnClickListener(
                v -> {
                    if (enabled) {
                        Toast.makeText(
                                        this,
                                        "Los avisos se enviarán cerca de la hora diaria elegida.",
                                        Toast.LENGTH_SHORT)
                                .show();
                    } else {
                        explainAndRequestNotificationPermission();
                    }
                });
        content.addView(notifications);

        addDashboard();
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

    private void addDashboard() {
        if (topics.isEmpty()) {
            return;
        }
        DashboardModel model =
                DashboardModel.build(
                        topics,
                        memoryEngine,
                        System.currentTimeMillis(),
                        ZoneId.systemDefault());

        TextView title = text("Resumen operativo", 20, Color.rgb(215, 205, 250));
        title.setPadding(4, 24, 4, 8);
        content.addView(title);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 14, 16, 16);
        card.setBackground(round(Color.rgb(42, 42, 46), 14));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, 8);
        card.setLayoutParams(cardParams);

        card.addView(
                text(
                        "Ahora: "
                                + model.dueNow
                                + " pendientes · "
                                + model.missed
                                + " omitidos · próximos 7 días: "
                                + model.upcomingSevenDays,
                        15,
                        Color.WHITE));

        String memorySummary =
                model.reviewedTopics == 0
                        ? "Aún no hay suficientes repasos para estimar el recuerdo medio."
                        : "Recuerdo medio: "
                                + Math.round(model.averageRetrievability * 100)
                                + "% · Bajo objetivo: "
                                + model.belowTarget;
        TextView memoryLine = text(memorySummary, 14, Color.LTGRAY);
        memoryLine.setPadding(0, 6, 0, 0);
        card.addView(memoryLine);

        String punctuality =
                model.completedOnTime + model.completedLate == 0
                        ? "Puntualidad: sin suficientes eventos"
                        : "Puntualidad: "
                                + Math.round(model.punctualityRate * 100)
                                + "% · Repasos últimos 7 días: "
                                + model.reviewedLastSevenDays;
        TextView punctualityLine = text(punctuality, 14, Color.LTGRAY);
        punctualityLine.setPadding(0, 4, 0, 10);
        card.addView(punctualityLine);

        ReviewLoadView loadView = new ReviewLoadView(this, model);
        card.addView(loadView, new LinearLayout.LayoutParams(-1, dp(205)));
        content.addView(card);

        TextView priorityTitle = text("Prioridades", 19, Color.rgb(215, 205, 250));
        priorityTitle.setPadding(4, 14, 4, 6);
        content.addView(priorityTitle);
        if (model.priorities.isEmpty()) {
            content.addView(
                    text(
                            "No hay repasos vencidos ni temas por debajo del objetivo.",
                            14,
                            Color.LTGRAY));
            return;
        }

        for (DashboardModel.PriorityItem item : model.priorities) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(14, 10, 14, 10);
            row.setBackground(round(Color.rgb(42, 42, 46), 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, 2, 0, 3);
            row.setLayoutParams(params);

            TextView percent =
                    text(
                            Math.round(item.retrievability * 100) + "%",
                            19,
                            priorityColor(item.kind));
            percent.setGravity(Gravity.CENTER);
            row.addView(percent, new LinearLayout.LayoutParams(64, 54));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(item.topic.name, 16, Color.WHITE));
            labels.addView(
                    text(
                            priorityLabel(item.kind) + " · " + dueText(item.topic),
                            13,
                            Color.LTGRAY));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(v -> showPage(item.topic));
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
        ReminderNotifications.cancelTopic(this, topic.id);
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
        content.addView(
                text(
                        "Objetivo de recuerdo: "
                                + StudyPreferences.percentageLabel(
                                        memoryEngine.desiredRetention())
                                + " · Próximo repaso: "
                                + dueText(topic),
                        16,
                        Color.DKGRAY));

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
                                        ReminderScheduler.ensureDaily(this);
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
                                        ReminderScheduler.cancelTopicSnooze(this, topic.id);
                                        ReminderScheduler.ensureDaily(this);
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

    private void chooseRetention() {
        String[] labels = {
            "85% — menos carga de repasos",
            "90% — equilibrio predeterminado",
            "95% — mayor frecuencia de repasos"
        };
        int[] selected = {
            StudyPreferences.optionIndex(memoryEngine.desiredRetention())
        };
        new AlertDialog.Builder(this)
                .setTitle("Objetivo de recuerdo")
                .setMessage(
                        "El objetivo controla los intervalos que FSRS calculará en repasos futuros. Las fechas ya programadas no se reescriben.")
                .setSingleChoiceItems(
                        labels,
                        selected[0],
                        (dialog, which) -> selected[0] = which)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                        "Guardar",
                        (dialog, which) -> {
                            double value = StudyPreferences.RETENTION_OPTIONS[selected[0]];
                            StudyPreferences.writeDesiredRetention(prefs, value);
                            if (repository != null) {
                                repository.close();
                            }
                            memoryEngine = new FsrsMemoryEngine(value);
                            repository = new StudyRepository(this, memoryEngine);
                            showHome();
                            Toast.makeText(
                                            this,
                                            "Objetivo actualizado. Se aplicará al próximo intervalo calculado.",
                                            Toast.LENGTH_LONG)
                                    .show();
                        })
                .show();
    }

    private void chooseTime(boolean first) {
        TimePickerDialog dialog =
                new TimePickerDialog(
                        this,
                        (view, hour, minute) -> {
                            reminderHour = hour;
                            reminderMinute = minute;
                            ReminderScheduler.configureDaily(this, hour, minute);
                            Toast.makeText(
                                            this,
                                            String.format(
                                                    Locale.getDefault(),
                                                    "Recordatorio diario configurado a las %02d:%02d",
                                                    hour,
                                                    minute),
                                            Toast.LENGTH_SHORT)
                                    .show();
                            explainAndRequestNotificationPermission();
                            if (first && pendingTopicId > 0) {
                                navigateAfterLoad();
                            } else {
                                showHome();
                            }
                        },
                        reminderHour,
                        reminderMinute,
                        true);
        dialog.setTitle(first ? "¿A qué hora quieres estudiar?" : "Hora de recordatorios");
        dialog.show();
    }

    private void explainAndRequestNotificationPermission() {
        if (ReminderNotifications.notificationsEnabled(this)) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                            this,
                            "Activa las notificaciones de la aplicación desde los ajustes del sistema.",
                            Toast.LENGTH_LONG)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Activar recordatorios")
                .setMessage(
                        "La aplicación usará notificaciones para avisarte de los temas que ya alcanzaron su fecha de repaso. Posponer un aviso no modifica la curva ni registra un repaso.")
                .setNegativeButton("Ahora no", null)
                .setPositiveButton(
                        "Activar",
                        (dialog, which) ->
                                requestPermissions(
                                        new String[] {Manifest.permission.POST_NOTIFICATIONS},
                                        REQUEST_NOTIFICATIONS))
                .show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        boolean granted =
                grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        Toast.makeText(
                        this,
                        granted
                                ? "Recordatorios activados"
                                : "No se podrán mostrar recordatorios hasta que habilites las notificaciones.",
                        Toast.LENGTH_LONG)
                .show();
        showHome();
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
        if (memoryPercent < memoryEngine.desiredRetention() * 100.0) {
            return Color.rgb(246, 201, 84);
        }
        return Color.rgb(102, 204, 169);
    }

    private int priorityColor(DashboardModel.PriorityKind kind) {
        return switch (kind) {
            case MISSED -> Color.rgb(237, 101, 135);
            case DUE -> Color.rgb(246, 150, 74);
            case BELOW_TARGET -> Color.rgb(246, 201, 84);
            case UPCOMING -> Color.rgb(112, 158, 246);
        };
    }

    private static String priorityLabel(DashboardModel.PriorityKind kind) {
        return switch (kind) {
            case MISSED -> "Repaso omitido";
            case DUE -> "Repaso pendiente";
            case BELOW_TARGET -> "Por debajo del objetivo";
            case UPCOMING -> "Próximo repaso";
        };
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
