package com.curvadeolvido.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.animation.ValueAnimator;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final int[] INTERVALS = {0, 1, 3, 7, 14, 30, 60, 120};
    private static final long DAY = 86400000L;
    private static final int[] TOPIC_COLORS = {
            Color.rgb(73, 139, 255), Color.rgb(102, 204, 169), Color.rgb(238, 157, 91),
            Color.rgb(202, 120, 230), Color.rgb(237, 101, 135), Color.rgb(246, 201, 84)
    };
    private static final int BLUE = Color.rgb(55, 115, 220);
    private SharedPreferences prefs;
    private LinearLayout content;
    private final ArrayList<Topic> topics = new ArrayList<>();
    private final HashSet<String> openCategories = new HashSet<>();
    private final HashSet<String> openSections = new HashSet<>();
    private int reminderHour = 19, reminderMinute = 0;
    private String pendingPhoto = "";
    private TextView photoStatus;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("curva", MODE_PRIVATE);
        load();
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 22);
        }
        showHome();
        if (!prefs.getBoolean("configured", false)) chooseTime(true); else scheduleDailyReminder();
    }

    private void showHome() {
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(22, 28, 22, 40); content.setBackgroundColor(Color.rgb(29,29,31));
        scroll.addView(content); setContentView(scroll);
        content.addView(text("Biblioteca de estudio", 28, Color.WHITE));
        TextView sub = text("Categorias, secciones y paginas", 15, Color.rgb(198,184,245)); sub.setPadding(0, 6, 0, 16); content.addView(sub);
        Button create = primaryButton("+ Nueva pagina"); create.setOnClickListener(v -> addPage()); content.addView(create);
        Button clock = darkButton("Hora de recordatorios"); clock.setOnClickListener(v -> chooseTime(false)); content.addView(clock);
        if (!topics.isEmpty()) content.addView(new GeneralCurveView(this, new ArrayList<>(topics)));
        addForgettingSummary();

        TreeMap<String, TreeMap<String, ArrayList<Topic>>> library = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Topic topic : topics) {
            String category = topic.subject.trim().isEmpty() ? "Sin categoria" : topic.subject;
            String section = topic.section.trim().isEmpty() ? "General" : topic.section;
            if (!library.containsKey(category)) library.put(category, new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
            if (!library.get(category).containsKey(section)) library.get(category).put(section, new ArrayList<>());
            library.get(category).get(section).add(topic);
        }
        if (openCategories.isEmpty()) openCategories.addAll(library.keySet());
        for (String category : library.keySet()) {
            addCategoryHeader(category, library.get(category).size());
            if (!openCategories.contains(category)) continue;
            for (String section : library.get(category).keySet()) {
                String key = category + "|" + section;
                if (!openSections.contains(key)) openSections.add(key);
                addSectionHeader(category, section, library.get(category).get(section).size());
                if (openSections.contains(key)) for (Topic topic : library.get(category).get(section)) content.addView(pageCard(topic));
            }
        }
        if (topics.isEmpty()) {
            TextView empty = text("Crea una categoria, una seccion y tu primera pagina para comenzar.", 16, Color.LTGRAY);
            empty.setPadding(8, 24, 8, 0); content.addView(empty);
        }
    }

    private void addForgettingSummary() {
        if (topics.isEmpty()) return;
        TextView title = text("Lo que mas se esta olvidando", 19, Color.rgb(215,205,250));
        title.setPadding(4, 24, 4, 7); content.addView(title);
        ArrayList<Topic> ordered = new ArrayList<>(topics);
        Collections.sort(ordered, (a, b) -> Float.compare(currentMemory(a), currentMemory(b)));
        for (int i = 0; i < Math.min(3, ordered.size()); i++) {
            Topic topic = ordered.get(i); LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(12,8,12,8); row.setBackground(round(Color.rgb(42,42,46), 12));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.setMargins(0,2,0,2); row.setLayoutParams(rowParams);
            row.addView(new HeadMemoryView(this, currentMemory(topic)), new LinearLayout.LayoutParams(46,58));
            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); labels.addView(text(topic.name,16,Color.WHITE)); labels.addView(text(Math.round(currentMemory(topic)) + "% de memoria estimada",13,Color.LTGRAY)); row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            row.setOnClickListener(v -> showPage(topic)); content.addView(row);
        }
    }

    private void addCategoryHeader(String category, int sections) {
        Button header = new Button(this); header.setAllCaps(false); header.setText((openCategories.contains(category) ? "v  " : ">  ") + "[Libro]  " + category + "  (" + sections + ")");
        header.setTextSize(19); header.setTextColor(Color.WHITE); header.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT); header.setBackground(round(Color.rgb(67,67,72), 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 58); lp.setMargins(0, 20, 0, 5); header.setLayoutParams(lp);
        header.setOnClickListener(v -> { if (openCategories.contains(category)) openCategories.remove(category); else openCategories.add(category); showHome(); });
        content.addView(header);
    }

    private void addSectionHeader(String category, String section, int pages) {
        String key = category + "|" + section;
        Button header = new Button(this); header.setAllCaps(false); header.setText((openSections.contains(key) ? "v  " : ">  ") + section + "  (" + pages + ")");
        header.setTextSize(17); header.setTextColor(Color.rgb(222,222,226)); header.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT); header.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 48); lp.setMargins(18, 3, 0, 0); header.setLayoutParams(lp);
        header.setOnClickListener(v -> { if (openSections.contains(key)) openSections.remove(key); else openSections.add(key); showHome(); });
        content.addView(header);
    }

    private View pageCard(Topic topic) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(18, 14, 18, 14); card.setBackground(round(Color.rgb(46,46,50), 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(30, 2, 0, 2); card.setLayoutParams(lp);
        TextView icon = text("[]", 25, Color.rgb(162,197,255)); card.addView(icon, new LinearLayout.LayoutParams(48, -2));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(topic.name, 18, Color.WHITE));
        labels.addView(text("Memoria " + Math.round(currentMemory(topic)) + "%  -  " + dueText(topic), 13, Color.LTGRAY));
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        card.setOnClickListener(v -> showPage(topic));
        return card;
    }

    private void showPage(Topic topic) {
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(28, 28, 28, 45); content.setBackgroundColor(Color.rgb(247,249,253)); scroll.addView(content); setContentView(scroll);
        Button back = darkButton("< Volver a la biblioteca"); back.setOnClickListener(v -> showHome()); content.addView(back);
        content.addView(text(topic.subject + "  >  " + topic.section, 15, BLUE));
        TextView title = text(topic.name, 28, Color.rgb(23,32,51)); title.setPadding(0, 10, 0, 10); content.addView(title);
        content.addView(new CurveView(this, topic, nextTime(topic)));
        TextView memory = text("Tema aprendido: " + topic.progress + "%  -  Memoria estimada: " + Math.round(currentMemory(topic)) + "%", 17, BLUE); memory.setPadding(0, 14, 0, 4); content.addView(memory);
        content.addView(text("Proximo repaso: " + dueText(topic), 16, Color.DKGRAY));
        content.addView(text("Pagina: escribe lo que estudiaste", 16, Color.DKGRAY));
        EditText editor = new EditText(this); editor.setText(topic.notes); editor.setTextSize(17); editor.setTextColor(Color.rgb(23,32,51)); editor.setGravity(Gravity.TOP | Gravity.LEFT); editor.setMinLines(8); editor.setHint("Escribe tus apuntes de esta pagina"); editor.setPadding(20,20,20,20); editor.setBackground(round(Color.rgb(232,243,255), 16)); content.addView(editor);
        Button save = primaryButton("Guardar pagina"); save.setOnClickListener(v -> { topic.notes = editor.getText().toString(); save(); Toast.makeText(this, "Pagina guardada", Toast.LENGTH_SHORT).show(); }); content.addView(save);
        Button register = primaryButton("Registrar estudio de hoy"); register.setOnClickListener(v -> logStudy(topic)); content.addView(register);
        content.addView(text("Calendario de repasos", 21, Color.rgb(23,32,51)));
        for (int i = topic.step; i < INTERVALS.length; i++) content.addView(text("- " + dateFor(topic, i) + "  repaso " + (i + 1), 15, Color.DKGRAY));
        content.addView(text("Historial", 21, Color.rgb(23,32,51)));
        if (topic.logs.isEmpty()) content.addView(text("Aun no hay registros.", 15, Color.DKGRAY));
        for (Log log : topic.logs) addLogView(log);
    }

    private void addLogView(Log log) {
        content.addView(text("- " + log.date + ": " + log.percent + "% - " + log.studied + " - Metodo: " + log.method + (log.remaining.isEmpty() ? "" : " - Falta: " + log.remaining), 15, Color.DKGRAY));
        if (!log.photo.isEmpty()) try {
            byte[] raw = Base64.decode(log.photo, Base64.DEFAULT); ImageView photo = new ImageView(this); photo.setImageBitmap(BitmapFactory.decodeByteArray(raw, 0, raw.length)); photo.setAdjustViewBounds(true);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, 320); size.setMargins(0, 8, 0, 12); content.addView(photo, size);
        } catch (Exception ignored) { }
    }

    private void addPage() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(35, 8, 35, 0);
        EditText category = new EditText(this); category.setHint("Categoria (ej.: Medicina)"); form.addView(category);
        EditText section = new EditText(this); section.setHint("Seccion (ej.: Nefrologia)"); form.addView(section);
        EditText title = new EditText(this); title.setHint("Titulo de la pagina"); form.addView(title);
        EditText notes = new EditText(this); notes.setHint("Que estudiaste en esta pagina"); notes.setMinLines(3); form.addView(notes);
        new AlertDialog.Builder(this).setTitle("Nueva pagina").setView(form).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", (d,w) -> {
            if (!title.getText().toString().trim().isEmpty()) {
                Topic topic = new Topic(); topic.id = (int)System.currentTimeMillis(); topic.subject = category.getText().toString().trim(); topic.section = section.getText().toString().trim(); topic.name = title.getText().toString().trim(); topic.notes = notes.getText().toString().trim(); topic.created = System.currentTimeMillis(); topics.add(topic); save(); scheduleDailyReminder(); showHome();
            }
        }).show();
    }

    private void logStudy(Topic topic) {
        pendingPhoto = "";
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(35, 0, 35, 0);
        EditText percent = new EditText(this); percent.setInputType(2); percent.setHint("Porcentaje estudiado hoy (ej.: 20)"); form.addView(percent);
        EditText studied = new EditText(this); studied.setHint("Partes especificas que estudiaste"); form.addView(studied);
        EditText method = new EditText(this); method.setHint("Como estudiaste (lectura, ejercicios, tarjetas)"); form.addView(method);
        Spinner recall = new Spinner(this); String[] choices = {"Recorde el tema: normal", "Recorde el tema: dificil", "Recorde el tema: facil"}; recall.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, choices)); form.addView(recall);
        Button camera = new Button(this); camera.setText("Tomar foto de lo estudiado"); form.addView(camera);
        photoStatus = text("Sin foto adjunta", 14, Color.DKGRAY); form.addView(photoStatus);
        camera.setOnClickListener(v -> { try { startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), 501); } catch (Exception e) { Toast.makeText(this, "No hay camara disponible.", Toast.LENGTH_SHORT).show(); } });
        EditText remaining = new EditText(this); remaining.setHint("Que falta por estudiar (opcional)"); form.addView(remaining);
        new AlertDialog.Builder(this).setTitle("Registrar estudio").setMessage("La proxima fecha se adapta a como recuerdas el tema.").setView(form).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", (d,w) -> {
            try {
                int value = Math.min(100, Math.max(0, Integer.parseInt(percent.getText().toString())));
                if (studied.getText().toString().trim().isEmpty() || method.getText().toString().trim().isEmpty()) return;
                double factor = recall.getSelectedItemPosition() == 1 ? 0.70 : recall.getSelectedItemPosition() == 2 ? 1.30 : 1.0;
                Log log = new Log(); log.date = DateFormat.getDateInstance().format(new Date()); log.percent = value; log.studied = studied.getText().toString().trim(); log.method = method.getText().toString().trim(); log.remaining = remaining.getText().toString().trim(); log.photo = pendingPhoto; topic.logs.add(0, log);
                topic.progress = Math.min(100, topic.progress + value); topic.reviewProgress = Math.min(100, topic.reviewProgress + value); topic.consolidation = Math.min(100, topic.consolidation + (float)(value * 0.28 * factor)); topic.lastStudy = System.currentTimeMillis();
                if (topic.progress == 100 && topic.reviewProgress == 100) { topic.step = Math.min(topic.step + 1, INTERVALS.length - 1); topic.reviewProgress = 0; topic.intervalFactor = factor; topic.created = System.currentTimeMillis(); scheduleDailyReminder(); }
                save(); showPage(topic);
            } catch (Exception ignored) { Toast.makeText(this, "Escribe un porcentaje entre 0 y 100.", Toast.LENGTH_SHORT).show(); }
        }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 501 && resultCode == RESULT_OK && data != null && data.getExtras() != null) try {
            Bitmap photo = (Bitmap)data.getExtras().get("data"); ByteArrayOutputStream stream = new ByteArrayOutputStream(); photo.compress(Bitmap.CompressFormat.JPEG, 75, stream); pendingPhoto = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP); if (photoStatus != null) photoStatus.setText("Foto adjunta al registro");
        } catch (Exception e) { Toast.makeText(this, "No se pudo guardar la foto.", Toast.LENGTH_SHORT).show(); }
    }

    private void chooseTime(boolean first) {
        TimePickerDialog dialog = new TimePickerDialog(this, (v,h,m) -> { reminderHour = h; reminderMinute = m; prefs.edit().putInt("hour",h).putInt("minute",m).putBoolean("configured",true).apply(); scheduleDailyReminder(); Toast.makeText(this, "Recordatorios configurados", Toast.LENGTH_SHORT).show(); }, reminderHour, reminderMinute, true);
        dialog.setTitle(first ? "A que hora quieres estudiar?" : "Hora de recordatorios"); dialog.show();
    }

    private boolean isDue(Topic topic) { return System.currentTimeMillis() >= nextTime(topic); }
    static long nextTimeFor(Topic topic) { long days = INTERVALS[topic.step] == 0 ? 0 : Math.max(1, Math.round(INTERVALS[topic.step] * topic.intervalFactor)); return topic.created + days * DAY; }
    // Esta es la unica fuente de verdad: tarjetas, resumen y curvas usan este valor.
    static float memoryFor(Topic topic, long now) {
        float interval = Math.max((float) DAY, nextTimeFor(topic) - topic.created);
        long memoryStart = topic.lastStudy > 0 ? topic.lastStudy : topic.created;
        float elapsed = Math.max(0f, now - memoryStart) / interval;
        float strength = Math.max(0f, Math.min(100f, topic.consolidation));
        float rate = 5f - 4f * (strength / 100f);
        return Math.max(0f, Math.min(100f, (float) (strength * Math.exp(-rate * elapsed * 0.45f))));
    }
    private long nextTime(Topic topic) { return nextTimeFor(topic); }
    private float currentMemory(Topic topic) { return memoryFor(topic, System.currentTimeMillis()); }
    private String dueText(Topic topic) { return isDue(topic) ? "pendiente ahora" : DateFormat.getDateInstance().format(new Date(nextTime(topic))); }
    private String dateFor(Topic topic, int step) { long days = INTERVALS[step] == 0 ? 0 : Math.max(1, Math.round(INTERVALS[step] * topic.intervalFactor)); return DateFormat.getDateInstance().format(new Date(topic.created + days * DAY)); }
    private void scheduleDailyReminder() { Calendar c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, reminderHour); c.set(Calendar.MINUTE, reminderMinute); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1); Intent i = new Intent(this, ReminderReceiver.class).putExtra("daily", true); PendingIntent pi = PendingIntent.getBroadcast(this, 9001, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); ((AlarmManager)getSystemService(ALARM_SERVICE)).setInexactRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi); }
    private Button primaryButton(String label) { Button b = new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackground(round(BLUE, 16)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 8, 0, 5); b.setLayoutParams(p); return b; }
    private Button darkButton(String label) { Button b = new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackground(round(Color.rgb(58,58,62), 14)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 6, 0, 4); b.setLayoutParams(p); return b; }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private GradientDrawable round(int color, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape; }
    private int blueFor(float amount) { float k = Math.min(1, Math.max(0, amount / 100f)); return Color.rgb((int)(231-215*k), (int)(240-182*k), (int)(255-135*k)); }
    private void load() { reminderHour = prefs.getInt("hour",19); reminderMinute = prefs.getInt("minute",0); try { JSONArray array = new JSONArray(prefs.getString("topics","[]")); for (int i=0;i<array.length();i++) topics.add(Topic.from(array.getJSONObject(i))); } catch (Exception ignored) { } }
    private void save() { JSONArray array = new JSONArray(); for (Topic topic : topics) array.put(topic.json()); prefs.edit().putString("topics", array.toString()).apply(); }

    static class Log { String date, studied, method, remaining, photo=""; int percent; JSONObject json() { JSONObject o=new JSONObject(); try { o.put("date",date); o.put("studied",studied); o.put("method",method); o.put("remaining",remaining); o.put("photo",photo); o.put("percent",percent); } catch(Exception ignored){} return o; } static Log from(JSONObject o) throws Exception { Log log=new Log(); log.date=o.getString("date"); log.studied=o.getString("studied"); log.method=o.optString("method","No indicado"); log.remaining=o.optString("remaining"); log.photo=o.optString("photo",""); log.percent=o.getInt("percent"); return log; } }
    static class Topic { int id, progress, reviewProgress, step; float consolidation; double intervalFactor=1; String name="", subject="", section="", notes=""; long created, lastStudy; ArrayList<Log> logs=new ArrayList<>(); JSONObject json() { JSONObject o=new JSONObject(); try { o.put("id",id);o.put("name",name);o.put("subject",subject);o.put("section",section);o.put("notes",notes);o.put("progress",progress);o.put("reviewProgress",reviewProgress);o.put("consolidation",consolidation);o.put("intervalFactor",intervalFactor);o.put("step",step);o.put("created",created);o.put("lastStudy",lastStudy);JSONArray a=new JSONArray();for(Log log:logs)a.put(log.json());o.put("logs",a); } catch(Exception ignored){} return o; } static Topic from(JSONObject o) throws Exception { Topic t=new Topic();t.id=o.getInt("id");t.name=o.getString("name");t.subject=o.optString("subject","");t.section=o.optString("section","");t.notes=o.optString("notes","");t.progress=o.optInt("progress",0);t.reviewProgress=o.optInt("reviewProgress",t.progress);t.consolidation=(float)o.optDouble("consolidation",t.progress*0.28);t.intervalFactor=o.optDouble("intervalFactor",1);t.step=o.optInt("step",0);t.created=o.optLong("created",System.currentTimeMillis());t.lastStudy=o.optLong("lastStudy",t.created);JSONArray a=o.optJSONArray("logs");if(a!=null)for(int i=0;i<a.length();i++)t.logs.add(Log.from(a.getJSONObject(i)));return t; } }
    static int colorFor(Topic topic) { return TOPIC_COLORS[Math.abs(topic.id) % TOPIC_COLORS.length]; }

    static abstract class ChartView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ChartView(Context c) { super(c); }
        float xForDay(float left, float right, float day) { return left + (right - left) * Math.min(1f, Math.max(0f, day / 120f)); }
        float yForMemory(float top, float bottom, float memory) { return bottom - (bottom - top) * Math.max(0f, Math.min(100f, memory)) / 100f; }
        void drawAxes(Canvas canvas, float left, float right, float top, float bottom, int labelColor) {
            paint.setStrokeWidth(1); paint.setStyle(Paint.Style.STROKE); paint.setColor(Color.rgb(197, 207, 222));
            for (int percent = 0; percent <= 100; percent += 25) {
                float y = yForMemory(top, bottom, percent); canvas.drawLine(left, y, right, y, paint);
                paint.setStyle(Paint.Style.FILL); paint.setTextSize(12); paint.setColor(labelColor); canvas.drawText(percent + "%", 3, y + 4, paint); paint.setStyle(Paint.Style.STROKE); paint.setColor(Color.rgb(197, 207, 222));
            }
            for (int day : INTERVALS) {
                float x = xForDay(left, right, day); canvas.drawLine(x, top, x, bottom, paint);
                paint.setStyle(Paint.Style.FILL); paint.setTextSize(10); paint.setColor(labelColor); String label = day + "d"; canvas.drawText(label, x - paint.measureText(label) / 2, bottom + 18, paint); paint.setStyle(Paint.Style.STROKE); paint.setColor(Color.rgb(197, 207, 222));
            }
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(13); paint.setColor(labelColor); canvas.drawText("% de memoria", left, top - 10, paint); canvas.drawText("Tiempo desde el repaso completo", Math.max(left, right - 185), bottom + 38, paint);
        }
        void drawTopicLine(Canvas canvas, Topic topic, float left, float right, float top, float bottom, int color) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4); paint.setColor(color); Path line = new Path();
            for (float day = 0; day <= 120; day += 1) {
                long memoryStart = topic.lastStudy > 0 ? topic.lastStudy : topic.created;
                float x = xForDay(left, right, day); float y = yForMemory(top, bottom, memoryFor(topic, memoryStart + (long) (day * DAY)));
                if (day == 0) line.moveTo(x, y); else line.lineTo(x, y);
            }
            canvas.drawPath(line, paint);
        }
    }

    static class GeneralCurveView extends ChartView {
        final ArrayList<Topic> topics;
        GeneralCurveView(Context c, ArrayList<Topic> items) { super(c); topics = items; setMinimumHeight(390); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), left = 45, right = w - 16, top = 62, bottom = h - 110;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(42, 47, 57)); canvas.drawRoundRect(0, 0, w, h, 22, 22, paint);
            paint.setColor(Color.WHITE); paint.setTextSize(22); canvas.drawText("Memoria general", 20, 32, paint);
            drawAxes(canvas, left, right, top, bottom, Color.rgb(220, 226, 236));
            int row = 0;
            for (Topic topic : topics) {
                int color = colorFor(topic); drawTopicLine(canvas, topic, left, right, top, bottom, color);
                float y = h - 64 + (row / 3) * 24, x = 18 + (row % 3) * (w - 28) / 3f;
                paint.setStyle(Paint.Style.FILL); paint.setColor(color); canvas.drawCircle(x, y - 4, 5, paint); paint.setColor(Color.WHITE); paint.setTextSize(12);
                String name = topic.name.length() > 14 ? topic.name.substring(0, 13) + "..." : topic.name; canvas.drawText(name, x + 10, y, paint); row++;
            }
        }
    }

    static class CurveView extends ChartView {
        final Topic topic; final Handler handler = new Handler(Looper.getMainLooper()); float reveal;
        final Runnable tick = new Runnable() { public void run() { invalidate(); handler.postDelayed(this, 1000); } };
        CurveView(Context c, Topic t, long ignoredNext) { super(c); topic = t; setMinimumHeight(340); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); ValueAnimator a = ValueAnimator.ofFloat(0f, 1f); a.setDuration(600); a.addUpdateListener(v -> { reveal = (float) v.getAnimatedValue(); invalidate(); }); a.start(); handler.post(tick); }
        @Override protected void onDetachedFromWindow() { handler.removeCallbacks(tick); super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), left = 45, right = w - 16, top = 70, bottom = h - 92;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(232, 242, 255)); canvas.drawRoundRect(0, 0, w, h, 22, 22, paint);
            paint.setColor(Color.rgb(16, 58, 120)); paint.setTextSize(23); canvas.drawText("Curva del tema", 20, 34, paint);
            drawAxes(canvas, left, right, top, bottom, Color.rgb(54, 66, 84)); drawTopicLine(canvas, topic, left, right, top, bottom, colorFor(topic));
            long now = System.currentTimeMillis(); long memoryStart = topic.lastStudy > 0 ? topic.lastStudy : topic.created; float elapsedDays = Math.max(0f, now - memoryStart) / DAY;
            float memory = memoryFor(topic, now); float x = xForDay(left, right, elapsedDays); float y = yForMemory(top, bottom, memory);
            drawHead(canvas, x, y, memory * reveal);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(23, 43, 70)); paint.setTextSize(16); canvas.drawText("Cabeza: " + Math.round(memory) + "% de memoria estimada", 20, h - 22, paint);
        }
        void drawHead(Canvas canvas, float x, float y, float memory) { Path head = headPath(x, y); paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE); canvas.drawPath(head, paint); canvas.save(); canvas.clipPath(head); float fillTop = y + 36 - 72 * memory / 100f; paint.setColor(Color.rgb(91, 202, 169)); canvas.drawRect(x - 29, fillTop, x + 29, y + 38, paint); canvas.restore(); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(Color.rgb(58, 68, 82)); canvas.drawPath(head, paint); }
        Path headPath(float x, float y) { Path p = new Path(); p.moveTo(x, y - 36); p.cubicTo(x - 27, y - 36, x - 30, y - 16, x - 29, y + 2); p.cubicTo(x - 28, y + 18, x - 18, y + 25, x - 13, y + 29); p.lineTo(x - 13, y + 38); p.quadTo(x, y + 44, x + 13, y + 38); p.lineTo(x + 13, y + 29); p.cubicTo(x + 18, y + 25, x + 28, y + 18, x + 29, y + 2); p.cubicTo(x + 30, y - 16, x + 27, y - 36, x, y - 36); p.close(); return p; }
    }
    static class HeadMemoryView extends View { Paint paint=new Paint(1); float memory; HeadMemoryView(Context c,float value){super(c);memory=value;} @Override protected void onDraw(Canvas canvas){float x=getWidth()/2f,y=getHeight()/2f;Path p=new Path();p.moveTo(x,y-25);p.cubicTo(x-18,y-25,x-20,y-10,x-19,y+2);p.cubicTo(x-18,y+12,x-10,y+16,x-8,y+18);p.lineTo(x-8,y+25);p.lineTo(x+8,y+25);p.lineTo(x+8,y+18);p.cubicTo(x+10,y+16,x+18,y+12,x+19,y+2);p.cubicTo(x+20,y-10,x+18,y-25,x,y-25);p.close();paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawPath(p,paint);canvas.save();canvas.clipPath(p);paint.setColor(Color.rgb(91,202,169));canvas.drawRect(0,y+25-50*memory/100f,getWidth(),getHeight(),paint);canvas.restore();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(Color.rgb(110,120,135));canvas.drawPath(p,paint);} }
}
