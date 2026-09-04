package com.agentplatform.orchestrator.matching;

public enum RecommendationLevel {
    EXCELLENT_MATCH(90, 100, "Excellent Match"),
    STRONG_MATCH(75, 89, "Strong Match"),
    POSSIBLE_MATCH(60, 74, "Possible Match"),
    WEAK_MATCH(40, 59, "Weak Match"),
    POOR_MATCH(0, 39, "Poor Match");

    private final int minScore;
    private final int maxScore;
    private final String displayName;

    private RecommendationLevel(int minScore, int maxScore, String displayName) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.displayName = displayName;
    }

    public int getMinScore() {
        return this.minScore;
    }

    public int getMaxScore() {
        return this.maxScore;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public static RecommendationLevel fromScore(int score) {
        if (score >= 90) {
            return EXCELLENT_MATCH;
        }
        if (score >= 75) {
            return STRONG_MATCH;
        }
        if (score >= 60) {
            return POSSIBLE_MATCH;
        }
        if (score >= 40) {
            return WEAK_MATCH;
        }
        return POOR_MATCH;
    }
}

