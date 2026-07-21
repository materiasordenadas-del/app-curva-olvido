package com.curvadeolvido.app.domain;

public final class MemorySnapshot {
    public final double retrievability;
    public final double stabilityDays;
    public final double difficulty;
    public final long measuredAt;

    public MemorySnapshot(
            double retrievability,
            double stabilityDays,
            double difficulty,
            long measuredAt) {
        this.retrievability = retrievability;
        this.stabilityDays = stabilityDays;
        this.difficulty = difficulty;
        this.measuredAt = measuredAt;
    }
}
