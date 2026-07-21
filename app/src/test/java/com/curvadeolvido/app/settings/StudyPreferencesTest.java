package com.curvadeolvido.app.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StudyPreferencesTest {
    @Test
    public void normalizesToSupportedRetentionOptions() {
        assertEquals(0.85, StudyPreferences.normalizeDesiredRetention(0.84), 0.000001);
        assertEquals(0.90, StudyPreferences.normalizeDesiredRetention(0.91), 0.000001);
        assertEquals(0.95, StudyPreferences.normalizeDesiredRetention(0.97), 0.000001);
    }

    @Test
    public void resolvesOptionIndexes() {
        assertEquals(0, StudyPreferences.optionIndex(0.85));
        assertEquals(1, StudyPreferences.optionIndex(0.90));
        assertEquals(2, StudyPreferences.optionIndex(0.95));
    }
}
