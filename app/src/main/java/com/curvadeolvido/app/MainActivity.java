package com.curvadeolvido.app;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final long DAY = 86_400_000L;
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
                        "Cobertura y recuperación se calculan por separado",
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
        for (int i = 0; i < Math.min(3, ordered.size()); i++) {
            StudyTopic topic = ordered.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(12, 8, 12, 8);
            row.setBackground(round(Color.rgb(42, 42, 46), 12));

            row.addView(
                    new HeadMemoryView(this, (float) currentMemory(topic)),
                    new LinearLayout.LayoutParams(46, 58));
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(topic.name, 16, Color.WHITE));
            labels.addView(
                    text(
                            Math.round(currentMemory(topic))
                                    + "% de recuerdo estimado · "
                                    + dueText(topic),
                            13,
                            Color.LTGRAY));
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
        content.addView(new CurveView(this, topic, memoryEngine));

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
                                + oneDecimal(snapshot.difficulty),
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
                                + statusLabel(topic.scheduleStatus)
                                + " · "
                                + formatDateTime(topic.nextReviewAt),
                        15,
                        Color.DKGRAY));

        ArrayList<ScheduledReview> history = new ArrayList<>(topic.scheduledReviews);
        history.sort(
                Comparator.comparingLong((ScheduledReview item) -> item.scheduledAt).reversed());
        for (int i = 0; i < Math.min(5, history.size()); i++) {
            ScheduledReview review = history.get(i);
            content.addView(
                    text(
                            "• "
                                    + formatDateTime(review.scheduledAt)
                                    + " — "
                                    + statusLabel(review.status),
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
        if (memoryEngine.isDue(topic, System.currentTimeMillis())) {
            return topic.scheduleStatus == ReviewScheduleStatus.MISSED
                    ? "repaso omitido y pendiente"
                    : "pendiente ahora";
        }
        return formatDateTime(topic.nextReviewAt);
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

    static final class CurveView extends View {
        private final StudyTopic topic;
        private final FsrsMemoryEngine engine;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private float reveal;

        private final Runnable tick =
                new Runnable() {
                    @Override
                    public void run() {
                        invalidate();
                        handler.postDelayed(this, 60_000);
                    }
                };

        CurveView(Context context, StudyTopic topic, FsrsMemoryEngine engine) {
            super(context);
            this.topic = topic;
            this.engine = engine;
            setMinimumHeight(340);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(600);
            animator.addUpdateListener(
                    value -> {
                        reveal = (float) value.getAnimatedValue();
                        invalidate();
                    });
            animator.start();
            handler.post(tick);
        }

        @Override
        protected void onDetachedFromWindow() {
            handler.removeCallbacks(tick);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float width = getWidth();
            float height = getHeight();
            float left = 45;
            float right = width - 16;
            float top = 70;
            float bottom = height - 92;

            long start = topic.lastReviewAt > 0 ? topic.lastReviewAt : topic.createdAt;
            float dueDays = Math.max(1f, (topic.nextReviewAt - start) / (float) DAY);
            float horizonDays = Math.min(365f, Math.max(7f, dueDays * 2f));

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(232, 242, 255));
            canvas.drawRoundRect(0, 0, width, height, 22, 22, paint);
            paint.setColor(Color.rgb(16, 58, 120));
            paint.setTextSize(23);
            canvas.drawText("Curva del tema", 20, 34, paint);

            drawAxes(canvas, left, right, top, bottom, horizonDays);
            drawCurve(canvas, left, right, top, bottom, start, horizonDays);

            long now = System.currentTimeMillis();
            float elapsedDays = Math.max(0f, now - start) / (float) DAY;
            float memory = (float) (engine.retrievability(topic, now) * 100.0);
            float x = xForDay(left, right, elapsedDays, horizonDays);
            float y = yForMemory(top, bottom, memory);
            drawHead(canvas, x, y, memory * reveal);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(23, 43, 70));
            paint.setTextSize(16);
            canvas.drawText(
                    "Recuerdo: "
                            + Math.round(memory)
                            + "% · Olvido: "
                            + Math.round(100 - memory)
                            + "%",
                    20,
                    height - 22,
                    paint);
        }

        private void drawAxes(
                Canvas canvas,
                float left,
                float right,
                float top,
                float bottom,
                float horizonDays) {
            paint.setStrokeWidth(1);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.rgb(197, 207, 222));
            for (int percent = 0; percent <= 100; percent += 25) {
                float y = yForMemory(top, bottom, percent);
                canvas.drawLine(left, y, right, y, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(12);
                paint.setColor(Color.rgb(54, 66, 84));
                canvas.drawText(percent + "%", 3, y + 4, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.rgb(197, 207, 222));
            }
            for (int index = 0; index <= 4; index++) {
                float day = horizonDays * index / 4f;
                float x = xForDay(left, right, day, horizonDays);
                canvas.drawLine(x, top, x, bottom, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(10);
                paint.setColor(Color.rgb(54, 66, 84));
                canvas.drawText(Math.round(day) + "d", x - 8, bottom + 18, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.rgb(197, 207, 222));
            }
        }

        private void drawCurve(
                Canvas canvas,
                float left,
                float right,
                float top,
                float bottom,
                long start,
                float horizonDays) {
            int samples = 120;
            long stepMillis = (long) (horizonDays * DAY / samples);
            double[] projected =
                    engine.retrievabilitySeries(topic, start, stepMillis, samples + 1);
            Path path = new Path();
            for (int sample = 0; sample <= samples; sample++) {
                float day = horizonDays * sample / samples;
                float x = xForDay(left, right, day, horizonDays);
                float y = yForMemory(top, bottom, (float) (projected[sample] * 100.0));
                if (sample == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            paint.setColor(BLUE);
            canvas.drawPath(path, paint);
        }

        private void drawHead(Canvas canvas, float x, float y, float memory) {
            Path head = headPath(x, y);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawPath(head, paint);
            canvas.save();
            canvas.clipPath(head);
            float fillTop = y + 36 - 72 * memory / 100f;
            paint.setColor(Color.rgb(91, 202, 169));
            canvas.drawRect(x - 29, fillTop, x + 29, y + 38, paint);
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.rgb(58, 68, 82));
            canvas.drawPath(head, paint);
        }

        private static Path headPath(float x, float y) {
            Path path = new Path();
            path.moveTo(x, y - 36);
            path.cubicTo(x - 27, y - 36, x - 30, y - 16, x - 29, y + 2);
            path.cubicTo(x - 28, y + 18, x - 18, y + 25, x - 13, y + 29);
            path.lineTo(x - 13, y + 38);
            path.quadTo(x, y + 44, x + 13, y + 38);
            path.lineTo(x + 13, y + 29);
            path.cubicTo(x + 18, y + 25, x + 28, y + 18, x + 29, y + 2);
            path.cubicTo(x + 30, y - 16, x + 27, y - 36, x, y - 36);
            path.close();
            return path;
        }

        private static float xForDay(
                float left, float right, float day, float horizonDays) {
            return left
                    + (right - left)
                            * Math.min(1f, Math.max(0f, day / Math.max(1f, horizonDays)));
        }

        private static float yForMemory(
                float top, float bottom, float memory) {
            return bottom
                    - (bottom - top)
                            * Math.max(0f, Math.min(100f, memory))
                            / 100f;
        }
    }

    static final class HeadMemoryView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float memory;

        HeadMemoryView(Context context, float memory) {
            super(context);
            this.memory = memory;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float x = getWidth() / 2f;
            float y = getHeight() / 2f;
            Path path = CurveView.headPath(x, y);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawPath(path, paint);
            canvas.save();
            canvas.clipPath(path);
            paint.setColor(Color.rgb(91, 202, 169));
            canvas.drawRect(0, y + 25 - 50 * memory / 100f, getWidth(), getHeight(), paint);
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(Color.rgb(110, 120, 135));
            canvas.drawPath(path, paint);
        }
    }
}
