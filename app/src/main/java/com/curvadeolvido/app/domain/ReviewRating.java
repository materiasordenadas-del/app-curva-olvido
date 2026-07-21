package com.curvadeolvido.app.domain;

import io.github.openspacedrepetition.Rating;

public enum ReviewRating {
    AGAIN,
    HARD,
    GOOD,
    EASY;

    public Rating toFsrsRating() {
        return switch (this) {
            case AGAIN -> Rating.AGAIN;
            case HARD -> Rating.HARD;
            case GOOD -> Rating.GOOD;
            case EASY -> Rating.EASY;
        };
    }

    public boolean isRetrievalFailure() {
        return this == AGAIN;
    }

    public String labelEs() {
        return switch (this) {
            case AGAIN -> "No lo recordé";
            case HARD -> "Difícil";
            case GOOD -> "Bien";
            case EASY -> "Fácil";
        };
    }

    public static ReviewRating fromSpinnerPosition(int position) {
        return switch (position) {
            case 0 -> AGAIN;
            case 1 -> HARD;
            case 3 -> EASY;
            default -> GOOD;
        };
    }
}
