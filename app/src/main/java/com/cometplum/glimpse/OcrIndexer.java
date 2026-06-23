package com.cometplum.glimpse;

import android.content.Context;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.List;

public class OcrIndexer {
    public interface ProgressListener {
        void onProgress(int done, int total, String message);
        void onComplete(int indexed, int skipped, int failed);
    }

    private final Context context;
    private final ScreenshotRepository repo;
    private final TextRecognizer recognizer;
    private int indexed;
    private int skipped;
    private int failed;

    public OcrIndexer(Context context, ScreenshotRepository repo) {
        this.context = context.getApplicationContext();
        this.repo = repo;
        this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public void indexAll(List<ScreenshotItem> items, ProgressListener listener) {
        indexed = 0;
        skipped = 0;
        failed = 0;
        indexNext(items, 0, listener);
    }

    private void indexNext(List<ScreenshotItem> items, int position, ProgressListener listener) {
        int total = items.size();
        if (position >= total) {
            listener.onComplete(indexed, skipped, failed);
            return;
        }

        ScreenshotItem item = items.get(position);
        if (repo.hasIndexed(item.mediaId)) {
            skipped++;
            listener.onProgress(position + 1, total, "Skipped already indexed: " + item.displayName);
            indexNext(items, position + 1, listener);
            return;
        }

        listener.onProgress(position, total, "OCR: " + item.displayName);
        try {
            InputImage image = InputImage.fromFilePath(context, item.uri);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        item.ocrText = text.getText();
                        CategoryGuess guess = CategoryGuesser.guess(item.ocrText, item);
                        item.category = guess.category;
                        item.tags = guess.tags;
                        item.confidence = guess.confidence;
                        item.semanticText = guess.semanticText;
                        repo.upsert(item);
                        indexed++;
                        listener.onProgress(position + 1, total, "Indexed: " + item.displayName);
                        indexNext(items, position + 1, listener);
                    })
                    .addOnFailureListener(e -> {
                        item.ocrText = "";
                        CategoryGuess guess = CategoryGuesser.guess("", item);
                        item.category = guess.category;
                        item.tags = guess.tags;
                        item.confidence = guess.confidence;
                        item.semanticText = guess.semanticText;
                        repo.upsert(item);
                        failed++;
                        listener.onProgress(position + 1, total, "OCR failed but metadata saved: " + item.displayName);
                        indexNext(items, position + 1, listener);
                    });
        } catch (Exception e) {
            failed++;
            listener.onProgress(position + 1, total, "Could not open: " + item.displayName);
            indexNext(items, position + 1, listener);
        }
    }
}
