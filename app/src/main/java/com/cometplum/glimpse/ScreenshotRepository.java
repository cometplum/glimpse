package com.cometplum.glimpse;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScreenshotRepository extends SQLiteOpenHelper {
    private static final String DB_NAME = "glimpse.db";
    private static final int DB_VERSION = 1;
    private static final String COLUMNS = "media_id,uri,display_name,bucket,relative_path,width,height,taken_at," +
            "substr(ocr_text,1,280) AS ocr_text,category,tags,substr(semantic_text,1,420) AS semantic_text,confidence";

    public ScreenshotRepository(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.enableWriteAheadLogging();
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
        db.execSQL("CREATE INDEX idx_screenshots_taken_at ON screenshots(taken_at DESC)");
        db.execSQL("CREATE INDEX idx_screenshots_category ON screenshots(category)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS screenshot_fts");
        db.execSQL("DROP TABLE IF EXISTS screenshots");
        onCreate(db);
    }

    public boolean hasIndexed(long mediaId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT 1 FROM screenshots WHERE media_id=? LIMIT 1", new String[]{String.valueOf(mediaId)})) {
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
        db.beginTransactionNonExclusive();
        try {
            ContentValues values = new ContentValues(14);
            values.put("media_id", item.mediaId);
            values.put("uri", item.uri.toString());
            values.put("display_name", safe(item.displayName));
            values.put("bucket", safe(item.bucket));
            values.put("relative_path", safe(item.relativePath));
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
            ContentValues fts = new ContentValues(6);
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
        List<ScreenshotItem> out = new ArrayList<>(Math.min(limit, 64));
        try (Cursor c = db.rawQuery("SELECT " + COLUMNS + " FROM screenshots ORDER BY taken_at DESC LIMIT ?", new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public List<ScreenshotItem> search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return recent(limit);

        SQLiteDatabase db = getReadableDatabase();
        Map<Long, ScreenshotItem> candidates = new HashMap<>(180);
        long now = System.currentTimeMillis();

        String match = SemanticLite.toFtsQuery(q);
        if (!match.isEmpty()) {
            String sql = "SELECT " + prefixColumns() + " FROM screenshot_fts f JOIN screenshots s ON s.media_id=f.docid " +
                    "WHERE screenshot_fts MATCH ? ORDER BY s.taken_at DESC LIMIT 180";
            try (Cursor c = db.rawQuery(sql, new String[]{match})) {
                while (c.moveToNext()) put(candidates, fromCursor(c));
            } catch (Exception ignored) {
                candidates.clear();
            }
        }

        likeCandidates(db, candidates, q, 120);

        if (candidates.size() < limit * 2) {
            String sql = "SELECT " + COLUMNS + " FROM screenshots ORDER BY taken_at DESC LIMIT 360";
            try (Cursor c = db.rawQuery(sql, null)) {
                while (c.moveToNext()) {
                    ScreenshotItem item = fromCursor(c);
                    if (SearchRanker.isGoodMatch(item, q, now)) put(candidates, item);
                }
            }
        }

        ArrayList<Scored> scored = new ArrayList<>(candidates.size());
        for (ScreenshotItem item : candidates.values()) {
            double score = SearchRanker.score(item, q, now);
            if (score >= 18) scored.add(new Scored(item, score));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        List<ScreenshotItem> out = new ArrayList<>(Math.min(limit, scored.size()));
        for (int i = 0; i < scored.size() && out.size() < limit; i++) out.add(scored.get(i).item);
        return out;
    }

    private void likeCandidates(SQLiteDatabase db, Map<Long, ScreenshotItem> out, String query, int limit) {
        List<String> terms = importantTerms(SemanticLite.expand(query));
        int added = 0;
        for (String term : terms) {
            if (term.length() < 3 || added >= limit) continue;
            String like = "%" + term.toLowerCase(Locale.US) + "%";
            String sql = "SELECT " + COLUMNS + " FROM screenshots WHERE lower(tags) LIKE ? OR lower(category) LIKE ? OR lower(display_name) LIKE ? OR lower(semantic_text) LIKE ? " +
                    "ORDER BY taken_at DESC LIMIT 40";
            try (Cursor c = db.rawQuery(sql, new String[]{like, like, like, like})) {
                while (c.moveToNext() && added < limit) {
                    ScreenshotItem item = fromCursor(c);
                    if (!out.containsKey(item.mediaId)) added++;
                    put(out, item);
                }
            }
        }
    }

    private List<String> importantTerms(String text) {
        String[] raw = SemanticLite.normalize(text).split("[^a-z0-9₹]+", -1);
        ArrayList<String> out = new ArrayList<>(12);
        for (String s : raw) {
            if (s.length() < 3 || out.contains(s)) continue;
            out.add(s);
            if (out.size() >= 12) break;
        }
        return out;
    }

    private void put(Map<Long, ScreenshotItem> map, ScreenshotItem item) {
        if (item != null) map.put(item.mediaId, item);
    }

    private static String prefixColumns() {
        return "s.media_id,s.uri,s.display_name,s.bucket,s.relative_path,s.width,s.height,s.taken_at," +
                "substr(s.ocr_text,1,280) AS ocr_text,s.category,s.tags,substr(s.semantic_text,1,420) AS semantic_text,s.confidence";
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

    private static final class Scored {
        final ScreenshotItem item;
        final double score;
        Scored(ScreenshotItem item, double score) { this.item = item; this.score = score; }
    }
}
