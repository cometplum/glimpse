package com.example.screenshotbrain;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 88;
    private ScreenshotRepository repo;
    private TextView status;
    private EditText searchBox;
    private LinearLayout resultList;
    private Button scanButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new ScreenshotRepository(this);
        buildUi();
        refreshRecent();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(12));
        root.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText("Screenshot Brain");
        title.setTextSize(26);
        title.setTextColor(0xFF111827);
        title.setGravity(Gravity.START);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Private offline screenshot search. Only screenshot candidates are indexed.");
        sub.setTextSize(13);
        sub.setTextColor(0xFF4B5563);
        sub.setPadding(0, dp(4), 0, dp(12));
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button permissionButton = new Button(this);
        permissionButton.setText("Allow images");
        permissionButton.setOnClickListener(v -> requestImagePermission());
        actions.addView(permissionButton, new LinearLayout.LayoutParams(0, -2, 1));

        scanButton = new Button(this);
        scanButton.setText("Scan screenshots");
        scanButton.setOnClickListener(v -> startScan());
        actions.addView(scanButton, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(0xFF374151);
        status.setPadding(0, dp(8), 0, dp(10));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        searchBox = new EditText(this);
        searchBox.setHint("Search: phonepe payment, train ticket, error screen...");
        searchBox.setSingleLine(true);
        searchBox.setTextSize(15);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { runSearch(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(searchBox, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        hint.setText("Local semantic-lite search expands meaning terms, then searches OCR/category/tags using SQLite FTS.");
        hint.setTextSize(12);
        hint.setTextColor(0xFF6B7280);
        hint.setPadding(0, dp(6), 0, dp(8));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scrollView = new ScrollView(this);
        resultList = new LinearLayout(this);
        resultList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(resultList);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
    }

    private void startScan() {
        if (!hasImagePermission()) {
            requestImagePermission();
            return;
        }
        scanButton.setEnabled(false);
        status.setText("Finding screenshot candidates from MediaStore metadata...");
        List<ScreenshotItem> candidates = ScreenshotScanner.findScreenshotCandidates(this, 5000);
        status.setText("Found " + candidates.size() + " screenshot candidates. Starting local OCR...");

        OcrIndexer indexer = new OcrIndexer(this, repo);
        indexer.indexAll(candidates, new OcrIndexer.ProgressListener() {
            @Override
            public void onProgress(int done, int total, String message) {
                runOnUiThread(() -> status.setText(done + "/" + total + " · " + message));
            }

            @Override
            public void onComplete(int indexed, int skipped, int failed) {
                runOnUiThread(() -> {
                    scanButton.setEnabled(true);
                    status.setText("Done. Indexed " + indexed + ", skipped " + skipped + ", OCR failed " + failed + ". Total local index: " + repo.countIndexed());
                    runSearch(searchBox.getText().toString());
                });
            }
        });
    }

    private void runSearch(String query) {
        List<ScreenshotItem> items = repo.search(query, 80);
        renderResults(items, query == null || query.trim().isEmpty() ? "Recent indexed screenshots" : "Results");
    }

    private void refreshRecent() {
        status.setText("Local index: " + repo.countIndexed() + " screenshots");
        renderResults(repo.recent(50), "Recent indexed screenshots");
    }

    private void renderResults(List<ScreenshotItem> items, String heading) {
        resultList.removeAllViews();
        TextView h = new TextView(this);
        h.setText(heading + " · " + items.size());
        h.setTextSize(16);
        h.setTextColor(0xFF111827);
        h.setPadding(0, dp(10), 0, dp(8));
        resultList.addView(h);

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No indexed screenshots yet. Tap Scan screenshots first.");
            empty.setTextColor(0xFF6B7280);
            empty.setTextSize(14);
            empty.setPadding(0, dp(24), 0, dp(24));
            resultList.addView(empty);
            return;
        }

        for (ScreenshotItem item : items) {
            resultList.addView(rowFor(item));
        }
    }

    private View rowFor(ScreenshotItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(0xFFFFFFFF);

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(0xFFE5E7EB);
        try { thumb.setImageURI(item.uri); } catch (Exception ignored) {}
        row.addView(thumb, new LinearLayout.LayoutParams(dp(64), dp(96)));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(12), 0, 0, 0);

        TextView category = new TextView(this);
        category.setText(item.category + " · " + confidence(item.confidence));
        category.setTextColor(0xFF4F46E5);
        category.setTextSize(14);
        textCol.addView(category);

        TextView name = new TextView(this);
        name.setText(item.displayName == null ? "Screenshot" : item.displayName);
        name.setTextColor(0xFF111827);
        name.setTextSize(15);
        name.setMaxLines(1);
        textCol.addView(name);

        TextView meta = new TextView(this);
        meta.setText(formatDate(item.takenAt) + " · " + item.width + "×" + item.height);
        meta.setTextColor(0xFF6B7280);
        meta.setTextSize(12);
        textCol.addView(meta);

        TextView snippet = new TextView(this);
        snippet.setText(snippet(item.ocrText, item.tags));
        snippet.setTextColor(0xFF374151);
        snippet.setTextSize(13);
        snippet.setMaxLines(3);
        textCol.addView(snippet);

        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> openImage(item.uri));
        return row;
    }

    private String snippet(String text, String tags) {
        String s = (text == null || text.trim().isEmpty()) ? tags : text.replace('\n', ' ').trim();
        if (s.length() > 180) return s.substring(0, 180) + "…";
        return s;
    }

    private String confidence(double c) {
        if (c <= 0) return "low confidence";
        return String.format(Locale.US, "%.0f%%", c * 100.0);
    }

    private String formatDate(long ms) {
        if (ms <= 0) return "unknown date";
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
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{imagePermission()}, PERMISSION_REQUEST);
        }
    }

    private String imagePermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            status.setText(ok ? "Permission granted. Tap Scan screenshots." : "Image permission denied.");
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
