package com.cometplum.glimpse;

public class CategoryGuesser {
    public static CategoryGuess guess(String rawText, String displayName) {
        return TagBrain.guess(rawText, displayName, "", "");
    }

    public static CategoryGuess guess(String rawText, ScreenshotItem item) {
        return TagBrain.guess(rawText, item.displayName, item.bucket, item.relativePath);
    }
}
