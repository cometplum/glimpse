package com.example.screenshotbrain;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScreenshotScanner {
    public static List<ScreenshotItem> findScreenshotCandidates(Context context, int max) {
        List<ScreenshotItem> out = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        ArrayList<String> cols = new ArrayList<>();
        cols.add(MediaStore.Images.Media._ID);
        cols.add(MediaStore.Images.Media.DISPLAY_NAME);
        cols.add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
        cols.add(MediaStore.Images.Media.WIDTH);
        cols.add(MediaStore.Images.Media.HEIGHT);
        cols.add(MediaStore.Images.Media.DATE_TAKEN);
        cols.add(MediaStore.Images.Media.DATE_ADDED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cols.add(MediaStore.Images.Media.RELATIVE_PATH);
        }

        String sort = MediaStore.Images.Media.DATE_ADDED + " DESC";
        try (Cursor cursor = resolver.query(collection, cols.toArray(new String[0]), null, null, sort)) {
            if (cursor == null) return out;
            while (cursor.moveToNext() && out.size() < max) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                String display = getString(cursor, MediaStore.Images.Media.DISPLAY_NAME);
                String bucket = getString(cursor, MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                String relPath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? getString(cursor, MediaStore.Images.Media.RELATIVE_PATH) : "";
                int width = getInt(cursor, MediaStore.Images.Media.WIDTH);
                int height = getInt(cursor, MediaStore.Images.Media.HEIGHT);
                long taken = getLong(cursor, MediaStore.Images.Media.DATE_TAKEN);
                if (taken <= 0) taken = getLong(cursor, MediaStore.Images.Media.DATE_ADDED) * 1000L;

                if (!isScreenshot(display, bucket, relPath, width, height)) continue;

                ScreenshotItem item = new ScreenshotItem();
                item.mediaId = id;
                item.uri = ContentUris.withAppendedId(collection, id);
                item.displayName = display;
                item.bucket = bucket;
                item.relativePath = relPath;
                item.width = width;
                item.height = height;
                item.takenAt = taken;
                out.add(item);
            }
        }
        return out;
    }

    private static boolean isScreenshot(String display, String bucket, String path, int width, int height) {
        String joined = ((display == null ? "" : display) + " " +
                (bucket == null ? "" : bucket) + " " +
                (path == null ? "" : path)).toLowerCase(Locale.US);
        boolean nameHit = joined.contains("screenshot") || joined.contains("screen_shot") ||
                joined.contains("screen-shot") || joined.contains("screenshots");
        boolean dimensionsLookScreenLike = width > 0 && height > 0 && Math.max(width, height) >= 1000 && Math.min(width, height) >= 480;
        return nameHit && dimensionsLookScreenLike;
    }

    private static String getString(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 && !c.isNull(idx) ? c.getString(idx) : "";
    }

    private static int getInt(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 && !c.isNull(idx) ? c.getInt(idx) : 0;
    }

    private static long getLong(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 && !c.isNull(idx) ? c.getLong(idx) : 0L;
    }
}
