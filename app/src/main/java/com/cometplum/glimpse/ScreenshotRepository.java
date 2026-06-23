package com.cometplum.glimpse;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScreenshotRepository extends SQLiteOpenHelper {
    private static final String DB_NAME = "glimpse.db";
    private static final int DB_VERSION = 1;

    public ScreenshotRepository(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE screenshots (" +
                "media_id INTEGER PRIMARY KEY," +
                "uri TEXT NOT NULL," +
                "display_name TEXT," +
                "bucket TEXT," +
                "relative_path TEXT," +
                "width INTEGER," +
                "height INTEGER," +
                "taken_at INTEGER," +
                "ocr_text TEXT," +
                "category TEXT," +
                "tags TEXT," +
                "semantic_text TEXT," +
                "confidence REAL," +
                "updated_at INTEGER" +
                ")");
        db.execSQL("CREATE VIRTUAL TABLE screenshot_fts USING fts4(text, title, category, tags, semantic)");
        db.execSQL("CREATE INDEX idx_screenshots_category ON screenshots(category)");
        db.execSQL("CREATE INDEX idx_screenshots_taken_at ON screenshots(taken_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS screenshot_fts");
        db.execSQL("DROP TABLE IF EXISTS screenshots");
        onCreate(db);
    }

    public boolean hasIndexed(long mediaId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT media_id FROM screenshots WHERE media_id=? LIMIT 1", new String[]{String.valueOf(mediaId)})) {
            return c.moveToFirst();
        }
    }

    public int countIndexed() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM screenshots", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void upsert(ScreenshotItem item) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("media_id", item.mediaId);
            values.put("uri", item.uri.toString());
            values.put("display_name", item.displayName);
            values.put("bucket", item.bucket);
            values.put("relative_path", item.relativePath);
            values.put("width", item.width);
            values.put("height", item.height);
            values.put("taken_at", item.takenAt);
            values.put("ocr_text", safe(item.ocrText));
            values.put("category", safe(item.category));
            values.put("tags", safe(item.tags));
            values.put("semantic_text", safe(item.semanticText));
            values.put("confidence", item.confidence);
            values.put("updated_at", System.currentTimeMillis());
            db.insertWithOnConflict("screenshots", null, values, SQLiteDatabase.CONFLICT_REPLACE);

            db.delete("screenshot_fts", "docid=?", new String[]{String.valueOf(item.mediaId)});
            ContentValues fts = new ContentValues();
            fts.put("docid", item.mediaId);
            fts.put("text", safe(item.ocrText));
            fts.put("title", safe(item.displayName));
            fts.put("category", safe(item.category));
            fts.put("tags", safe(item.tags));
            fts.put("semantic", safe(item.semanticText));
            db.insert("screenshot_fts", null, fts);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<ScreenshotItem> recent(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<ScreenshotItem> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT * FROM screenshots ORDER BY taken_at DESC LIMIT ?", new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public List<ScreenshotItem> search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return recent(limit);

        SQLiteDatabase db = getReadableDatabase();
        List<ScreenshotItem> out = new ArrayList<>();
        String match = SemanticLite.toFtsQuery(q);
        if (!match.isEmpty()) {
            String sql = "SELECT s.* FROM screenshot_fts f JOIN screenshots s ON s.media_id=f.docid " +
                    "WHERE screenshot_fts MATCH ? ORDER BY s.taken_at DESC LIMIT ?";
            try (Cursor c = db.rawQuery(sql, new String[]{match, String.valueOf(limit)})) {
                while (c.moveToNext()) out.add(fromCursor(c));
            } catch (Exception ignored) {
                out.clear();
            }
        }
        if (out.isEmpty()) {
            String like = "%" + q.toLowerCase(Locale.US) + "%";
            String sql = "SELECT * FROM screenshots WHERE lower(ocr_text) LIKE ? OR lower(semantic_text) LIKE ? " +
                    "OR lower(category) LIKE ? OR lower(tags) LIKE ? OR lower(display_name) LIKE ? " +
                    "ORDER BY taken_at DESC LIMIT ?";
            try (Cursor c = db.rawQuery(sql, new String[]{like, like, like, like, like, String.valueOf(limit)})) {
                while (c.moveToNext()) out.add(fromCursor(c));
            }
        }
        return out;
    }

    private ScreenshotItem fromCursor(Cursor c) {
        ScreenshotItem item = new ScreenshotItem();
        item.mediaId = c.getLong(c.getColumnIndexOrThrow("media_id"));
        item.uri = android.net.Uri.parse(c.getString(c.getColumnIndexOrThrow("uri")));
        item.displayName = c.getString(c.getColumnIndexOrThrow("display_name"));
        item.bucket = c.getString(c.getColumnIndexOrThrow("bucket"));
        item.relativePath = c.getString(c.getColumnIndexOrThrow("relative_path"));
        item.width = c.getInt(c.getColumnIndexOrThrow("width"));
        item.height = c.getInt(c.getColumnIndexOrThrow("height"));
        item.takenAt = c.getLong(c.getColumnIndexOrThrow("taken_at"));
        item.ocrText = c.getString(c.getColumnIndexOrThrow("ocr_text"));
        item.category = c.getString(c.getColumnIndexOrThrow("category"));
        item.tags = c.getString(c.getColumnIndexOrThrow("tags"));
        item.semanticText = c.getString(c.getColumnIndexOrThrow("semantic_text"));
        item.confidence = c.getDouble(c.getColumnIndexOrThrow("confidence"));
        return item;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
