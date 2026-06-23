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
            if (terms.size() >= 18) break;
        }
        ArrayList<String> fts = new ArrayList<>(terms.size());
        for (String term : terms) fts.add(term + "*");
        return joinOr(fts);
    }

    public static String expand(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder(q.length() + 256).append(q);

        if (hit(q, "aadhar", "aadhaar", "adhaar", "adhar", "uidai", "identity"))
            out.append(" aadhaar aadhar adhaar adhar uidai identity id card document proof government");
        if (hit(q, "money", "payment", "paid", "pay", "sent", "bank", "upi", "phonepe", "phone pe", "phonpe", "gpay", "google pay", "paytm", "finance", "transaction", "txn"))
            out.append(" money payment transaction txn utr upi bank wallet receipt amount rupees debit credit phonepe phonpe googlepay gpay paytm");
        if (hit(q, "chess", "ches", "game", "gaming", "score", "level", "puzzle", "match", "player", "lichess"))
            out.append(" game gaming chess ches lichess chess.com match score level puzzle player win board checkmate rapid blitz bullet elo");
        if (hit(q, "food", "swiggy", "swigy", "zomato", "zomoto", "restaurant", "meal"))
            out.append(" food restaurant delivery order meal swiggy swigy zomato zomoto dining");
        if (hit(q, "ticket", "travel", "train", "trin", "flight", "pnr", "trip", "cab", "route", "irctc"))
            out.append(" travel ticket pnr train flight boarding route cab platform seat irctc ola uber");
        if (hit(q, "otp", "ot", "login", "password", "verification", "verify", "code"))
            out.append(" otp login verification password security code authenticate two factor 2fa");
        if (hit(q, "bug", "error", "crash", "exception", "api", "gradle", "gradel", "github", "git hub"))
            out.append(" code error exception crash bug build api developer gradle gradel github git hub kotlin java stacktrace failed");
        if (hit(q, "order", "shopping", "shoping", "delivery", "amazon", "flipkart", "invoice", "receipt"))
            out.append(" shopping order delivery invoice product cart refund return amazon flipkart myntra receipt");
        if (hit(q, "chat", "message", "whatsapp", "telegram", "dm"))
            out.append(" chat message whatsapp telegram signal dm reply conversation contact");
        if (hit(q, "map", "maps", "location", "route", "direction", "near"))
            out.append(" map maps location route directions navigation traffic km nearby destination");
        return out.toString();
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.US).replace('\n', ' ').replace('\t', ' ').trim();
    }

    public static String skeleton(String value) {
        String s = normalize(value);
        StringBuilder b = new StringBuilder(s.length());
        char prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c)) continue;
            if (c == prev) continue;
            prev = c;
            if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') continue;
            b.append(c);
        }
        return b.toString();
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
