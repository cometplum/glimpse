package com.cometplum.glimpse;

public class CategoryGuess {
    public final String category;
    public final String tags;
    public final double confidence;
    public final String semanticText;

    public CategoryGuess(String category, String tags, double confidence, String semanticText) {
        this.category = category;
        this.tags = tags;
        this.confidence = confidence;
        this.semanticText = semanticText;
    }
}
