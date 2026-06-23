package com.cometplum.glimpse;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class TagBrain {
    private TagBrain() {}

    public static CategoryGuess guess(String rawText, String displayName, String bucket, String path) {
        String text = norm(rawText) + " " + norm(displayName) + " " + norm(bucket) + " " + norm(path);
        Score best = new Score("General", "screenshot", 0);
        Score[] scores = new Score[] {
                score("Money", "payment", text, 14, "₹", "rs", "inr", "upi", "utr", "txn", "transaction", "paid", "debited", "credited", "bank", "wallet", "phonepe", "gpay", "google pay", "paytm", "receipt"),
                score("Game", "game", text, 14, "chess", "lichess", "chess.com", "checkmate", "puzzle", "blitz", "rapid", "bullet", "elo", "game", "level", "score", "victory", "defeat", "match", "player", "pubg", "free fire", "minecraft", "roblox", "steam"),
                score("Chat", "message", text, 12, "whatsapp", "telegram", "signal", "message", "reply", "typing", "online", "last seen", "chat", "dm", "conversation"),
                score("Shopping", "order", text, 12, "order", "cart", "delivery", "delivered", "tracking", "amazon", "flipkart", "myntra", "invoice", "return", "refund", "product"),
                score("Travel", "ticket", text, 12, "pnr", "irctc", "train", "flight", "boarding", "gate", "platform", "departure", "arrival", "seat", "ticket", "uber", "ola", "route", "booking"),
                score("Food", "food order", text, 12, "swiggy", "zomato", "restaurant", "food", "burger", "pizza", "meal", "dining", "delivery partner", "ubereats"),
                score("Login", "otp", text, 12, "otp", "verification", "security code", "password", "login", "2fa", "authenticate", "do not share"),
                score("Code", "error", text, 11, "error", "exception", "stacktrace", "failed", "crash", "bug", "compile", "runtime", "localhost", "api", "json", "gradle", "kotlin", "java", "python", "github"),
                score("Work", "work", text, 10, "meeting", "calendar", "slack", "teams", "jira", "notion", "deadline", "task", "project", "standup", "docs", "sheet"),
                score("Document", "document", text, 10, "pdf", "document", "aadhaar", "aadhar", "pan", "passport", "certificate", "statement", "form", "receipt", "invoice", "resume"),
                score("Map", "location", text, 10, "maps", "location", "directions", "km", "traffic", "near me", "route", "navigate", "destination"),
                score("Social", "social", text, 9, "instagram", "facebook", "x.com", "twitter", "reddit", "post", "reel", "story", "like", "comment", "followers"),
                score("Media", "media", text, 8, "youtube", "netflix", "prime video", "spotify", "song", "movie", "episode", "playlist", "watch"),
                score("Education", "study", text, 8, "class", "course", "lesson", "exam", "quiz", "assignment", "marks", "grade", "school", "college", "learn"),
                score("Health", "health", text, 8, "doctor", "hospital", "medicine", "appointment", "lab", "report", "blood", "prescription", "health"),
                score("Sports", "sports", text, 8, "scorecard", "cricket", "football", "ipl", "match", "wicket", "goal", "score", "team")
        };

        for (Score s : scores) if (s.value > best.value) best = s;

        Set<String> tags = new LinkedHashSet<>();
        tags.add(best.tag);
        tags.add(best.category.toLowerCase(Locale.US));
        enrichTags(text, tags);

        double confidence = best.value <= 0 ? 0.18 : Math.min(0.96, 0.38 + best.value * 0.055);
        String tagText = join(tags);
        String semantic = buildSemantic(best.category, tagText, rawText, text);
        return new CategoryGuess(best.category, tagText, confidence, semantic);
    }

    private static Score score(String category, String tag, String text, int base, String... keys) {
        int v = 0;
        for (String k : keys) {
            if (text.contains(k)) v += weight(k, base);
        }
        return new Score(category, tag, v);
    }

    private static int weight(String key, int base) {
        if (key.length() <= 2) return Math.max(2, base / 4);
        if (key.length() <= 4) return Math.max(3, base / 3);
        return Math.max(4, base / 2);
    }

    private static void enrichTags(String t, Set<String> tags) {
        addIf(t, tags, "phonepe", "phonepe");
        addIf(t, tags, "gpay", "googlepay");
        addIf(t, tags, "google pay", "googlepay");
        addIf(t, tags, "paytm", "paytm");
        addIf(t, tags, "upi", "upi");
        addIf(t, tags, "utr", "utr");
        addIf(t, tags, "txn", "transaction");
        if (t.contains("₹") || t.contains(" rs ") || t.contains("inr")) tags.add("amount");

        addIf(t, tags, "chess", "chess");
        addIf(t, tags, "lichess", "lichess");
        addIf(t, tags, "chess.com", "chess.com");
        addIf(t, tags, "checkmate", "checkmate");
        addIf(t, tags, "puzzle", "puzzle");
        addIf(t, tags, "score", "score");
        addIf(t, tags, "level", "level");

        addIf(t, tags, "swiggy", "swiggy");
        addIf(t, tags, "zomato", "zomato");
        addIf(t, tags, "amazon", "amazon");
        addIf(t, tags, "flipkart", "flipkart");
        addIf(t, tags, "pnr", "pnr");
        addIf(t, tags, "irctc", "irctc");
        addIf(t, tags, "otp", "otp");
        addIf(t, tags, "whatsapp", "whatsapp");
        addIf(t, tags, "instagram", "instagram");
        addIf(t, tags, "youtube", "youtube");
        addIf(t, tags, "github", "github");
        addIf(t, tags, "gradle", "gradle");
    }

    private static String buildSemantic(String category, String tags, String raw, String text) {
        StringBuilder b = new StringBuilder(256);
        b.append(category).append(' ').append(tags).append(' ');
        if (raw != null) b.append(raw).append(' ');
        switch (category) {
            case "Money": b.append("finance money payment paid sent received transaction receipt bank upi wallet amount rupees debit credit"); break;
            case "Game": b.append("game gaming chess match player score level puzzle win loss board esport entertainment"); break;
            case "Chat": b.append("chat message conversation reply contact social communication"); break;
            case "Shopping": b.append("shopping order product delivery purchase invoice tracking refund return"); break;
            case "Travel": b.append("travel ticket train flight cab booking route trip journey seat pnr boarding"); break;
            case "Food": b.append("food order delivery restaurant meal dining"); break;
            case "Login": b.append("otp login verification password security authentication code"); break;
            case "Code": b.append("programming developer code error bug crash exception stacktrace build api"); break;
            case "Map": b.append("map location route directions navigation traffic distance"); break;
            default: b.append("screenshot image saved phone local"); break;
        }
        if (text.matches(".*\\b[0-9]{4,6}\\b.*")) b.append(" code id number amount otp pnr score");
        return b.toString();
    }

    private static void addIf(String t, Set<String> tags, String key, String tag) {
        if (t.contains(key)) tags.add(tag);
    }

    private static String join(Set<String> tags) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        for (String s : tags) {
            if (i++ > 0) b.append(' ');
            b.append(s);
            if (i >= 10) break;
        }
        return b.toString();
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US).replace('\n', ' ').replace('\t', ' ');
    }

    private static final class Score {
        final String category;
        final String tag;
        final int value;
        Score(String category, String tag, int value) { this.category = category; this.tag = tag; this.value = value; }
    }
}
