package com.curvadeolvido.app.notifications;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Pure date/time calculations for reminder scheduling. */
public final class ReminderTiming {
    public static final long MINUTE_MS = 60_000L;

    private ReminderTiming() {}

    public static long nextDailyAtMillis(
            long nowMillis, int hour, int minute, ZoneId zoneId) {
        validateTime(hour, minute);
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zoneId);
        ZonedDateTime target =
                now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!target.isAfter(now)) {
            target = target.plusDays(1);
        }
        return target.toInstant().toEpochMilli();
    }

    public static long tomorrowAtMillis(
            long nowMillis, int hour, int minute, ZoneId zoneId) {
        validateTime(hour, minute);
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zoneId);
        return now.plusDays(1)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)
                .toInstant()
                .toEpochMilli();
    }

    public static long delayUntil(long nowMillis, long targetMillis) {
        return Math.max(0L, targetMillis - nowMillis);
    }

    public static long delayMinutes(int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("minutes no puede ser negativo");
        }
        return minutes * MINUTE_MS;
    }

    private static void validateTime(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Hora inválida");
        }
    }
}
