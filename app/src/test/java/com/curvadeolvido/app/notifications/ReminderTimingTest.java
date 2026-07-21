package com.curvadeolvido.app.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;

public class ReminderTimingTest {
    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    @Test
    public void schedulesSameDayWhenTimeHasNotPassed() {
        long now = ZonedDateTime.of(2026, 7, 21, 10, 0, 0, 0, ZONE).toInstant().toEpochMilli();
        long target = ReminderTiming.nextDailyAtMillis(now, 19, 0, ZONE);
        ZonedDateTime value = Instant.ofEpochMilli(target).atZone(ZONE);
        assertEquals(21, value.getDayOfMonth());
        assertEquals(19, value.getHour());
    }

    @Test
    public void schedulesNextDayWhenTimeAlreadyPassed() {
        long now = ZonedDateTime.of(2026, 7, 21, 21, 0, 0, 0, ZONE).toInstant().toEpochMilli();
        long target = ReminderTiming.nextDailyAtMillis(now, 19, 0, ZONE);
        ZonedDateTime value = Instant.ofEpochMilli(target).atZone(ZONE);
        assertEquals(22, value.getDayOfMonth());
        assertEquals(19, value.getHour());
    }

    @Test
    public void tomorrowSnoozeUsesConfiguredLocalTime() {
        long now = ZonedDateTime.of(2026, 7, 21, 23, 30, 0, 0, ZONE).toInstant().toEpochMilli();
        long target = ReminderTiming.tomorrowAtMillis(now, 7, 15, ZONE);
        ZonedDateTime value = Instant.ofEpochMilli(target).atZone(ZONE);
        assertEquals(22, value.getDayOfMonth());
        assertEquals(7, value.getHour());
        assertEquals(15, value.getMinute());
    }

    @Test
    public void handlesDaylightSavingTransitionByLocalClock() {
        long now = ZonedDateTime.of(2026, 10, 31, 20, 0, 0, 0, ZONE).toInstant().toEpochMilli();
        long target = ReminderTiming.nextDailyAtMillis(now, 8, 0, ZONE);
        ZonedDateTime value = Instant.ofEpochMilli(target).atZone(ZONE);
        assertEquals(1, value.getDayOfMonth());
        assertEquals(8, value.getHour());
        assertTrue(target > now);
    }

    @Test
    public void minuteSnoozesAreExactElapsedDelays() {
        assertEquals(30 * 60_000L, ReminderTiming.delayMinutes(30));
        assertEquals(120 * 60_000L, ReminderTiming.delayMinutes(120));
    }
}
