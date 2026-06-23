package com.cometplum.glimpse;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 88;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ScreenshotRepository repo;
    private TextView status;
    private EditText searchBox;
    private LinearLayout resultList;
    private TextView indexAction;
    private int searchGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new ScreenshotRepository(this);
        buildUi();
        searchAsync("");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(12));
        root.setBackgroundColor(0xFFFAFAFA);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Glimpse");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF0F172A);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        indexAction = pill("Index", 0xFF111827, 0xFFFFFFFF);
        indexAction.setOnClickListener(v -> startScan());
        top.addView(indexAction, new LinearLayout.LayoutParams(-2, dp(42)));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        searchBox = new EditText(this);
        searchBox.setHint("Search screenshots by meaning");
        searchBox.setSingleLine(true);
        searchBox.setTextSize(17);
        searchBox.setTextColor(0xFF111827);
        searchBox.setHintTextColor(0xFF94A3B8);
        searchBox.setPadding(dp(18), 0, dp(18), 0);
        searchBox.setBackground(round(0xFFFFFFFF, dp(24), 0xFFE5E7EB));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(58));
        searchParams.setMargins(0, dp(16), 0, dp(8));
        root.addView(searchBox, searchParams);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleSearch(s == null ? "" : s.toString());
            }
        });

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(0xFF64748B);
        status.setPadding(dp(2), 0, 0, dp(8));
        status.setText("Private. Offline. Screenshot-only.");
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        resultList = new LinearLayout(this);
        resultList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(resultList, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
    }

    private void startScan() {
        if (!hasImagePermission()) {
            requestImagePermission();
            return;
        }
        indexAction.setEnabled(false);
        indexAction.setAlpha(0.55f);
        status.setText("Finding screenshots…");

        io.execute(() -> {
            List<ScreenshotItem> candidates = ScreenshotScanner.findScreenshotCandidates(this, 5000);
            main.post(() -> {
                status.setText(candidates.isEmpty() ? "No screenshot candidates found." : "Indexing " + candidates.size() + " screenshots locally…");
                OcrIndexer indexer = new OcrIndexer(this, repo);
                indexer.indexAll(candidates, new OcrIndexer.ProgressListener() {
                    @Override public void onProgress(int done, int total, String message) {
                        if (done % 5 == 0 || done == total) main.post(() -> status.setText(done + "/" + total + " indexed"));
                    }

                    @Override public void onComplete(int indexed, int skipped, int failed) {
                        main.post(() -> {
                            indexAction.setEnabled(true);
                            indexAction.setAlpha(1f);
                            status.setText(repo.countIndexed() + " screenshots ready");
                            searchAsync(searchBox.getText().toString());
                        });
                    }
                });
            });
        });
    }

    private void scheduleSearch(String q) {
        int gen = ++searchGeneration;
        main.postDelayed(() -> {
            if (gen == searchGeneration) searchAsync(q);
        }, 90);
    }

    private void searchAsync(String q) {
        int gen = ++searchGeneration;
        io.execute(() -> {
            List<ScreenshotItem> items = repo.search(q, 36);
            main.post(() -> {
                if (gen == searchGeneration) renderResults(items, q);
            });
        });
    }

    private void renderResults(List<ScreenshotItem> items, String query) {
        resultList.removeAllViews();
        int total = repo.countIndexed();
        String q = query == null ? "" : query.trim();
        status.setText(total == 0 ? "Tap Index once. Search is instant after that." : items.size() + " shown · " + total + " indexed");

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(total == 0 ? "Index screenshots to start." : "No match yet.");
            empty.setTextSize(16);
            empty.setTextColor(0xFF64748B);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(64), 0, dp(64));
            resultList.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (ScreenshotItem item : items) resultList.addView(rowFor(item));
    }

    private View rowFor(ScreenshotItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(round(0xFFFFFFFF, dp(22), 0xFFE5E7EB));
        row.setOnClickListener(v -> openImage(item.uri));

        TextView top = new TextView(this);
        top.setText(label(item));
        top.setTextSize(15);
        top.setTypeface(Typeface.DEFAULT_BOLD);
        top.setTextColor(0xFF0F172A);
        top.setMaxLines(1);
        row.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView tags = new TextView(this);
        tags.setText(cleanTags(item.tags));
        tags.setTextSize(13);
        tags.setTextColor(0xFF2563EB);
        tags.setPadding(0, dp(4), 0, 0);
        tags.setMaxLines(1);
        row.addView(tags, new LinearLayout.LayoutParams(-1, -2));

        String snip = snippet(item.ocrText, item.semanticText);
        if (!snip.isEmpty()) {
            TextView snippet = new TextView(this);
            snippet.setText(snip);
            snippet.setTextSize(13);
            snippet.setTextColor(0xFF475569);
            snippet.setPadding(0, dp(8), 0, 0);
            snippet.setMaxLines(2);
            row.addView(snippet, new LinearLayout.LayoutParams(-1, -2));
        }

        TextView date = new TextView(this);
        date.setText(formatDate(item.takenAt));
        date.setTextSize(12);
        date.setTextColor(0xFF94A3B8);
        date.setPadding(0, dp(8), 0, 0);
        row.addView(date, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);
        return row;
    }

    private String label(ScreenshotItem item) {
        String category = item.category == null || item.category.isEmpty() ? "Screenshot" : item.category;
        String tags = item.tags == null ? "" : item.tags;
        if (tags.contains("chess")) return "Chess screenshot";
        if (tags.contains("phonepe")) return "PhonePe screenshot";
        if (tags.contains("swiggy")) return "Swiggy screenshot";
        if (tags.contains("zomato")) return "Zomato screenshot";
        if (tags.contains("otp")) return "OTP screenshot";
        if (tags.contains("github") || tags.contains("gradle")) return "Developer screenshot";
        return category + " screenshot";
    }

    private String cleanTags(String tags) {
        if (tags == null || tags.trim().isEmpty()) return "screenshot";
        String[] parts = tags.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length && i < 5; i++) {
            if (i > 0) b.append(" · ");
            b.append(parts[i]);
        }
        return b.toString();
    }

    private String snippet(String text, String fallback) {
        String s = text == null ? "" : text.replace('\n', ' ').trim();
        if (s.isEmpty()) s = fallback == null ? "" : fallback.trim();
        if (s.length() > 150) return s.substring(0, 150) + "…";
        return s;
    }

    private String formatDate(long ms) {
        if (ms <= 0) return "";
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(ms));
    }

    private void openImage(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception ignored) {
            status.setText("Could not open screenshot.");
        }
    }

    private boolean hasImagePermission() {
        String permission = imagePermission();
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestImagePermission() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{imagePermission()}, PERMISSION_REQUEST);
    }

    private String imagePermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            status.setText(ok ? "Permission ready. Tap Index." : "Permission needed to index screenshots.");
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private TextView pill(String text, int bg, int fg) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(15);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(fg);
        v.setPadding(dp(18), 0, dp(18), 0);
        v.setBackground(round(bg, dp(22), bg));
        return v;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (stroke != color) d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
