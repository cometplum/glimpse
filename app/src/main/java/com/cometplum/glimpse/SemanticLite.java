package com.cometplum.glimpse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class SemanticLite {
    public static String toFtsQuery(String query) {
        String expanded = expand(query);
        String[] parts = expanded.toLowerCase(Locale.US).split("[^a-z0-9₹]+");
        Set<String> terms = new LinkedHashSet<>();
        for (String p : parts) {
            if (p.length() >= 2 && terms.size() < 24) terms.add(p);
        }
        ArrayList<String> ftsTerms = new ArrayList<>();
        for (String term : terms) {
            // FTS prefix matching: phone* finds phonepe, payment* finds payments.
            ftsTerms.add(term + "*");
        }
        return joinOr(ftsTerms);
    }

    public static String expand(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder(q);

        if (containsAny(q, "money", "payment", "paid", "pay", "sent", "received", "bank", "upi", "phonepe", "gpay", "paytm", "rupee", "finance", "transaction")) {
            out.append(" finance payment transaction paid received sent upi bank wallet receipt rupees amount debit credit phonepe gpay paytm");
        }
        if (containsAny(q, "food", "swiggy", "zomato", "restaurant", "meal", "dinner", "lunch")) {
            out.append(" food restaurant delivery order swiggy zomato meal");
        }
        if (containsAny(q, "ticket", "travel", "train", "flight", "pnr", "trip", "cab", "route")) {
            out.append(" travel ticket pnr train flight boarding gate platform departure route cab");
        }
        if (containsAny(q, "otp", "login", "password", "verification", "code")) {
            out.append(" otp login verification password security code authenticate");
        }
        if (containsAny(q, "bug", "error", "crash", "code", "exception", "api")) {
            out.append(" code error exception crash bug compile runtime api developer");
        }
        if (containsAny(q, "order", "shopping", "delivery", "amazon", "flipkart", "invoice")) {
            out.append(" shopping order delivery invoice product cart amazon flipkart refund return");
        }
        return out.toString();
    }

    private static boolean containsAny(String q, String... needles) {
        for (String n : needles) {
            if (q.contains(n)) return true;
        }
        return false;
    }

    private static String joinOr(ArrayList<String> terms) {
        if (terms.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append(terms.get(i));
        }
        return sb.toString();
    }
}
