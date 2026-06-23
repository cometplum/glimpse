package com.cometplum.glimpse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class SemanticLite {
    private SemanticLite() {}

    public static String toFtsQuery(String query) {
        String expanded = expand(query);
        String[] parts = expanded.toLowerCase(Locale.US).split("[^a-z0-9₹]+", -1);
        Set<String> terms = new LinkedHashSet<>();
        for (String p : parts) {
            if (p.length() >= 2) terms.add(p);
            if (terms.size() >= 12) break;
        }
        ArrayList<String> fts = new ArrayList<>(terms.size());
        for (String term : terms) fts.add(term + "*");
        return joinOr(fts);
    }

    public static String expand(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder(q.length() + 128).append(q);

        if (hit(q, "money", "payment", "paid", "pay", "sent", "bank", "upi", "phonepe", "gpay", "paytm", "finance", "transaction"))
            out.append(" money payment transaction upi bank wallet receipt amount");
        if (hit(q, "chess", "game", "gaming", "score", "level", "puzzle", "match", "player"))
            out.append(" game gaming chess match score level puzzle player win board");
        if (hit(q, "food", "swiggy", "zomato", "restaurant", "meal"))
            out.append(" food restaurant delivery order meal");
        if (hit(q, "ticket", "travel", "train", "flight", "pnr", "trip", "cab", "route"))
            out.append(" travel ticket pnr train flight boarding route cab");
        if (hit(q, "otp", "login", "password", "verification", "code"))
            out.append(" otp login verification password security code");
        if (hit(q, "bug", "error", "crash", "exception", "api", "gradle", "github"))
            out.append(" code error exception crash bug build api developer");
        if (hit(q, "order", "shopping", "delivery", "amazon", "flipkart", "invoice"))
            out.append(" shopping order delivery invoice product cart refund");
        return out.toString();
    }

    private static boolean hit(String q, String... needles) {
        for (String n : needles) if (q.contains(n)) return true;
        return false;
    }

    private static String joinOr(ArrayList<String> terms) {
        if (terms.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) b.append(" OR ");
            b.append(terms.get(i));
        }
        return b.toString();
    }
}
