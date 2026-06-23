package com.example.screenshotbrain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class CategoryGuesser {
    public static CategoryGuess guess(String rawText, String displayName) {
        String text = normalize((rawText == null ? "" : rawText) + " " + (displayName == null ? "" : displayName));

        Map<String, Integer> scores = new LinkedHashMap<>();
        addCategory(scores, "Finance", text,
                "upi", "utr", "transaction", "paid", "payment", "debited", "credited", "bank", "wallet", "phonepe", "gpay", "google pay", "paytm", "rs", "inr", "₹", "rupee", "ref no", "txn");
        addCategory(scores, "Travel", text,
                "pnr", "irctc", "train", "flight", "boarding", "gate", "platform", "departure", "arrival", "seat", "ticket", "cab", "uber", "ola", "route");
        addCategory(scores, "Shopping", text,
                "order", "cart", "delivered", "delivery", "tracking", "amazon", "flipkart", "myntra", "invoice", "return", "refund", "product");
        addCategory(scores, "Food", text,
                "swiggy", "zomato", "restaurant", "food", "burger", "pizza", "meal", "delivery partner", "order picked", "dining");
        addCategory(scores, "OTP / Login", text,
                "otp", "verification code", "login", "password", "2fa", "two factor", "security code", "do not share", "authenticate");
        addCategory(scores, "Chats", text,
                "whatsapp", "telegram", "signal", "message", "typing", "online", "last seen", "reply", "chat");
        addCategory(scores, "Work", text,
                "meeting", "calendar", "slack", "teams", "jira", "github", "notion", "deadline", "task", "project", "standup");
        addCategory(scores, "Code / Error", text,
                "error", "exception", "stacktrace", "failed", "crash", "bug", "compile", "runtime", "localhost", "api", "json", "gradle", "kotlin", "java");
        addCategory(scores, "Maps", text,
                "maps", "location", "directions", "km", "min", "traffic", "near me", "route", "navigate");
        addCategory(scores, "Documents", text,
                "pdf", "document", "aadhaar", "pan", "passport", "certificate", "statement", "form", "receipt", "invoice");

        String best = "Unknown";
        int bestScore = 0;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }

        String tags = buildTags(text, best);
        double confidence = bestScore == 0 ? 0.0 : Math.min(0.97, 0.45 + (bestScore * 0.08));
        String semantic = buildSemanticText(rawText, best, tags, text);
        return new CategoryGuess(best, tags, confidence, semantic);
    }

    private static void addCategory(Map<String, Integer> scores, String category, String text, String... needles) {
        int score = scores.containsKey(category) ? scores.get(category) : 0;
        for (String needle : needles) {
            if (text.contains(needle)) score++;
        }
        scores.put(category, score);
    }

    private static String buildTags(String text, String category) {
        StringBuilder tags = new StringBuilder(category.toLowerCase(Locale.US));
        if (text.contains("₹") || text.contains("rs") || text.contains("inr")) tags.append(" money amount rupees");
        if (text.contains("upi") || text.contains("utr") || text.contains("txn")) tags.append(" upi transaction receipt");
        if (text.contains("phonepe")) tags.append(" phonepe");
        if (text.contains("gpay") || text.contains("google pay")) tags.append(" googlepay");
        if (text.contains("paytm")) tags.append(" paytm");
        if (text.contains("swiggy")) tags.append(" swiggy food");
        if (text.contains("zomato")) tags.append(" zomato food");
        if (text.contains("pnr")) tags.append(" pnr ticket travel");
        if (text.contains("otp")) tags.append(" otp login code");
        if (text.contains("error") || text.contains("exception")) tags.append(" code bug crash");
        return tags.toString().trim();
    }

    private static String buildSemanticText(String rawText, String category, String tags, String normalizedText) {
        StringBuilder semantic = new StringBuilder();
        semantic.append(category).append(' ').append(tags).append(' ');
        semantic.append(rawText == null ? "" : rawText);

        if ("Finance".equals(category)) semantic.append(" payment money transaction bank receipt paid sent received debit credit UPI finance");
        if ("Travel".equals(category)) semantic.append(" ticket booking journey train flight cab route trip travel");
        if ("Shopping".equals(category)) semantic.append(" order product delivery cart invoice purchase shopping");
        if ("Food".equals(category)) semantic.append(" food delivery restaurant order meal");
        if ("OTP / Login".equals(category)) semantic.append(" login verification password security code otp");
        if ("Code / Error".equals(category)) semantic.append(" programming error bug exception crash code developer");
        if (normalizedText.matches(".*\\b[0-9]{4,6}\\b.*")) semantic.append(" code number id amount otp pnr");
        return semantic.toString();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US).replace('\n', ' ').replace('\t', ' ');
    }
}
