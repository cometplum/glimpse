package com.cometplum.glimpse;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 88;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbs = Executors.newFixedThreadPool(2);
    private final Map<Long, Bitmap> thumbCache = new LinkedHashMap<Long, Bitmap>(96, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Bitmap> eldest) { return size() > 96; }
    };

    private ScreenshotRepository repo;
    private TextView status;
    private TextView countBadge;
    private EditText searchBox;
    private LinearLayout resultList;
    private TextView indexAction;
    private int searchGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new ScreenshotRepository(this);
        setupWindow();
        buildUi();
        searchAsync("");
    }

    private void setupWindow() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(0xFFF4F7FB);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(liquidBg());

        View glowTop = new View(this);
        glowTop.setBackground(edgeGlow(0x55FFFFFF, 0x00FFFFFF, true));
        root.addView(glowTop, frame(-1, dp(130), Gravity.TOP));

        View glowBottom = new View(this);
        glowBottom.setBackground(edgeGlow(0x00FFFFFF, 0x66FFFFFF, false));
        root.addView(glowBottom, frame(-1, dp(150), Gravity.BOTTOM));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(38), dp(18), dp(96));
        root.addView(content, frame(-1, -1, Gravity.TOP));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Glimpse");
        title.setTextSize(31);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF07111F);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        countBadge = glassPill("0", 0xD9FFFFFF, 0xFF0F172A);
        header.addView(countBadge, new LinearLayout.LayoutParams(-2, dp(40)));
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout searchShell = new FrameLayout(this);
        searchShell.setPadding(dp(2), dp(2), dp(2), dp(2));
        searchShell.setBackground(round(0xBFFFFFFF, dp(28), 0x88FFFFFF));
        LinearLayout.LayoutParams shellParams = new LinearLayout.LayoutParams(-1, dp(60));
        shellParams.setMargins(0, dp(18), 0, dp(10));
        content.addView(searchShell, shellParams);

        searchBox = new EditText(this);
        searchBox.setHint("Search anything — aadhaar, chess, UPI, tickets…");
        searchBox.setSingleLine(true);
        searchBox.setTextSize(16);
        searchBox.setTextColor(0xFF07111F);
        searchBox.setHintTextColor(0xFF718096);
        searchBox.setPadding(dp(18), 0, dp(18), 0);
        searchBox.setBackgroundColor(Color.TRANSPARENT);
        searchShell.addView(searchBox, frame(-1, -1, Gravity.CENTER));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleSearch(s == null ? "" : s.toString());
            }
        });

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(0xFF5B6472);
        status.setText("Private local search");
        status.setPadding(dp(4), 0, 0, dp(8));
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        resultList = new LinearLayout(this);
        resultList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(resultList, new ScrollView.LayoutParams(-1, -2));
        content.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(8), dp(8), dp(8), dp(8));
        dock.setBackground(round(0xEFFFFFFF, dp(30), 0x99FFFFFF));

        indexAction = glassPill("Index screenshots", 0xFF07111F, 0xFFFFFFFF);
        indexAction.setOnClickListener(v -> startScan());
        dock.addView(indexAction, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView privatePill = glassPill("offline", 0x00FFFFFF, 0xFF334155);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, dp(48));
        pp.setMargins(dp(8), 0, 0, 0);
        dock.addView(privatePill, pp);

        FrameLayout.LayoutParams dockParams = frame(-1, dp(72), Gravity.BOTTOM);
        dockParams.setMargins(dp(18), 0, dp(18), dp(16));
        root.addView(dock, dockParams);

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
                status.setText(candidates.isEmpty() ? "No screenshots found" : "Indexing " + candidates.size() + " screenshots locally…");
                OcrIndexer indexer = new OcrIndexer(this, repo);
                indexer.indexAll(candidates, new OcrIndexer.ProgressListener() {
                    @Override public void onProgress(int done, int total, String message) {
                        if (done % 7 == 0 || done == total) main.post(() -> {
                            status.setText(done + "/" + total + " indexed");
                            countBadge.setText(String.valueOf(repo.countIndexed()));
                        });
                    }

                    @Override public void onComplete(int indexed, int skipped, int failed) {
                        main.post(() -> {
                            indexAction.setEnabled(true);
                            indexAction.setAlpha(1f);
                            status.setText(repo.countIndexed() + " ready");
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
        }, 55);
    }

    private void searchAsync(String q) {
        int gen = ++searchGeneration;
        io.execute(() -> {
            List<ScreenshotItem> items = repo.search(q, 42);
            int total = repo.countIndexed();
            main.post(() -> {
                if (gen == searchGeneration) renderResults(items, q, total);
            });
        });
    }

    private void renderResults(List<ScreenshotItem> items, String query, int total) {
        resultList.removeAllViews();
        countBadge.setText(total > 999 ? "999+" : String.valueOf(total));
        String q = query == null ? "" : query.trim();
        if (total == 0) status.setText("Tap Index once");
        else if (q.isEmpty()) status.setText("Recent screenshots");
        else status.setText(items.size() + " best matches");

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(total == 0 ? "Index screenshots to start." : "No strong match yet.");
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
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(12), dp(10));
        row.setBackground(round(0xEFFFFFFF, dp(24), 0xAAFFFFFF));
        row.setOnClickListener(v -> openViewer(item.uri));

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(round(0xFFE9EEF7, dp(18), 0x00FFFFFF));
        thumb.setClipToOutline(true);
        thumb.setTag(item.mediaId);
        row.addView(thumb, new LinearLayout.LayoutParams(dp(74), dp(74)));
        loadThumbAsync(item, thumb);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), 0, 0, 0);
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));

        TextView top = new TextView(this);
        top.setText(label(item));
        top.setTextSize(15);
        top.setTypeface(Typeface.DEFAULT_BOLD);
        top.setTextColor(0xFF07111F);
        top.setMaxLines(1);
        body.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView tags = new TextView(this);
        tags.setText(cleanTags(item.tags));
        tags.setTextSize(12);
        tags.setTextColor(0xFF2563EB);
        tags.setPadding(0, dp(4), 0, 0);
        tags.setMaxLines(1);
        body.addView(tags, new LinearLayout.LayoutParams(-1, -2));

        String snip = snippet(item.ocrText, item.semanticText);
        if (!snip.isEmpty()) {
            TextView snippet = new TextView(this);
            snippet.setText(snip);
            snippet.setTextSize(12);
            snippet.setTextColor(0xFF526072);
            snippet.setPadding(0, dp(5), 0, 0);
            snippet.setMaxLines(2);
            body.addView(snippet, new LinearLayout.LayoutParams(-1, -2));
        }

        TextView date = new TextView(this);
        date.setText(formatDate(item.takenAt));
        date.setTextSize(11);
        date.setTextColor(0xFF94A3B8);
        date.setPadding(0, dp(5), 0, 0);
        body.addView(date, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);
        return row;
    }

    private void loadThumbAsync(ScreenshotItem item, ImageView target) {
        Bitmap cached;
        synchronized (thumbCache) { cached = thumbCache.get(item.mediaId); }
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        target.setImageDrawable(null);
        thumbs.execute(() -> {
            Bitmap bmp = null;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    bmp = getContentResolver().loadThumbnail(item.uri, new Size(dp(148), dp(148)), null);
                } else {
                    bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), item.uri);
                }
            } catch (Exception ignored) {}
            if (bmp == null) return;
            synchronized (thumbCache) { thumbCache.put(item.mediaId, bmp); }
            Bitmap finalBmp = bmp;
            main.post(() -> {
                Object tag = target.getTag();
                if (tag instanceof Long && ((Long) tag) == item.mediaId) target.setImageBitmap(finalBmp);
            });
        });
    }

    private String label(ScreenshotItem item) {
        String category = item.category == null || item.category.isEmpty() ? "Screenshot" : item.category;
        String tags = item.tags == null ? "" : item.tags.toLowerCase(Locale.US);
        if (tags.contains("aadhaar") || tags.contains("uidai")) return "Aadhaar document";
        if (tags.contains("chess") || tags.contains("lichess")) return "Chess game";
        if (tags.contains("phonepe")) return "PhonePe payment";
        if (tags.contains("googlepay") || tags.contains("gpay")) return "Google Pay payment";
        if (tags.contains("swiggy")) return "Swiggy order";
        if (tags.contains("zomato")) return "Zomato order";
        if (tags.contains("otp")) return "Login code";
        if (tags.contains("github") || tags.contains("gradle")) return "Developer screenshot";
        if (category.equals("Finance")) return "Payment or finance";
        if (category.equals("Game")) return "Game screenshot";
        if (category.equals("Identity")) return "Identity document";
        return category;
    }

    private String cleanTags(String tags) {
        if (tags == null || tags.trim().isEmpty()) return "screenshot";
        String[] parts = tags.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length && i < 4; i++) {
            if (i > 0) b.append(" · ");
            b.append(parts[i]);
        }
        return b.toString();
    }

    private String snippet(String text, String fallback) {
        String s = text == null ? "" : text.replace('\n', ' ').trim();
        if (s.isEmpty()) s = fallback == null ? "" : fallback.trim();
        if (s.length() > 120) return s.substring(0, 120) + "…";
        return s;
    }

    private String formatDate(long ms) {
        if (ms <= 0) return "";
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(ms));
    }

    private void openViewer(Uri uri) {
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra("uri", uri.toString());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
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
            status.setText(ok ? "Ready to index" : "Permission needed");
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        thumbs.shutdownNow();
        super.onDestroy();
    }

    private TextView glassPill(String text, int bg, int fg) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(13);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(fg);
        v.setPadding(dp(16), 0, dp(16), 0);
        v.setBackground(round(bg, dp(24), 0x80FFFFFF));
        return v;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if ((stroke >>> 24) != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private GradientDrawable liquidBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFF8FBFF, 0xFFEFF6FF, 0xFFFDF7FF});
    }

    private GradientDrawable edgeGlow(int start, int end, boolean top) {
        return new GradientDrawable(top ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.BOTTOM_TOP, new int[]{start, end});
    }

    private FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h);
        p.gravity = gravity;
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
