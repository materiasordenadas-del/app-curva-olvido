package com.curvadeolvido.app.settings;

import android.content.SharedPreferences;
import java.util.Locale;

/** Persistent user preferences that influence future scheduling decisions. */
public final class StudyPreferences {
    public static final String KEY_DESIRED_RETENTION = "desired_retention";
    public static final double DEFAULT_DESIRED_RETENTION = 0.90;
    public static final double[] RETENTION_OPTIONS = {0.85, 0.90, 0.95};

    private StudyPreferences() {}

    public static double readDesiredRetention(SharedPreferences preferences) {
        double stored =
                preferences.getFloat(
                        KEY_DESIRED_RETENTION, (float) DEFAULT_DESIRED_RETENTION);
        return normalizeDesiredRetention(stored);
    }

    public static void writeDesiredRetention(
            SharedPreferences preferences, double desiredRetention) {
        preferences.edit()
                .putFloat(
                        KEY_DESIRED_RETENTION,
                        (float) normalizeDesiredRetention(desiredRetention))
                .apply();
    }

    public static double normalizeDesiredRetention(double value) {
        double nearest = RETENTION_OPTIONS[0];
        double distance = Math.abs(value - nearest);
        for (double option : RETENTION_OPTIONS) {
            double candidateDistance = Math.abs(value - option);
            if (candidateDistance < distance) {
                nearest = option;
                distance = candidateDistance;
            }
        }
        return nearest;
    }

    public static int optionIndex(double value) {
        double normalized = normalizeDesiredRetention(value);
        for (int index = 0; index < RETENTION_OPTIONS.length; index++) {
            if (Math.abs(RETENTION_OPTIONS[index] - normalized) < 0.000001) {
                return index;
            }
        }
        return 1;
    }

    public static String percentageLabel(double value) {
        return String.format(
                Locale.getDefault(),
                "%d%%",
                Math.round(normalizeDesiredRetention(value) * 100.0));
    }
}
