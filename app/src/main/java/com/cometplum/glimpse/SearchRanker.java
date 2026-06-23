package com.cometplum.glimpse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SearchRanker {
    private SearchRanker() {}

    public static double score(ScreenshotItem item, String query, long nowMs) {
        String q = SemanticLite.normalize(query);
        if (q.isEmpty()) return recency(item, nowMs);

        List<String> terms = terms(SemanticLite.expand(q));
        String tags = SemanticLite.normalize(item.tags);
        String category = SemanticLite.normalize(item.category);
        String semantic = SemanticLite.normalize(item.semanticText);
        String ocr = SemanticLite.normalize(item.ocrText);
        String title = SemanticLite.normalize(item.displayName);
        String all = tags + ' ' + category + ' ' + semantic + ' ' + ocr + ' ' + title;

        double score = 0;
        if (contains(all, q)) score += 120;
        if (contains(tags, q)) score += 80;
        if (contains(category, q)) score += 48;
        if (contains(semantic, q)) score += 46;
        if (contains(ocr, q)) score += 38;

        Set<String> itemTokens = new LinkedHashSet<>(terms(all));
        Set<String> itemSkeletons = new LinkedHashSet<>();
        int skeletonLimit = 0;
        for (String token : itemTokens) {
            if (++skeletonLimit > 220) break;
            if (token.length() >= 4) itemSkeletons.add(SemanticLite.skeleton(token));
        }

        for (String term : terms) {
            if (term.length() < 2) continue;
            score += tokenScore(term, tags, 46);
            score += tokenScore(term, category, 34);
            score += tokenScore(term, semantic, 25);
            score += tokenScore(term, ocr, 22);
            score += tokenScore(term, title, 18);

            if (term.length() >= 4) {
                String sk = SemanticLite.skeleton(term);
                if (!sk.isEmpty() && itemSkeletons.contains(sk)) score += 18;
            }
            if (term.length() >= 5 && fuzzyHit(term, itemTokens)) score += 14;
        }

        if (item.confidence > 0) score += Math.min(10, item.confidence * 10.0);
        score += recency(item, nowMs);
        return score;
    }

    public static boolean isGoodMatch(ScreenshotItem item, String query, long nowMs) {
        String q = SemanticLite.normalize(query);
        if (q.isEmpty()) return true;
        return score(item, q, nowMs) >= 24;
    }

    private static double tokenScore(String term, String text, int weight) {
        if (text == null || text.isEmpty()) return 0;
        if (contains(text, term)) return weight;
        return 0;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && needle != null && !needle.isEmpty() && haystack.contains(needle);
    }

    private static List<String> terms(String text) {
        String[] raw = SemanticLite.normalize(text).split("[^a-z0-9₹]+", -1);
        ArrayList<String> out = new ArrayList<>(Math.min(raw.length, 48));
        Set<String> seen = new LinkedHashSet<>();
        for (String s : raw) {
            if (s.length() < 2) continue;
            if (seen.add(s)) out.add(s);
            if (out.size() >= 48) break;
        }
        return out;
    }

    private static boolean fuzzyHit(String needle, Set<String> tokens) {
        int checked = 0;
        for (String token : tokens) {
            if (++checked > 220) return false;
            int diff = Math.abs(token.length() - needle.length());
            if (diff > 2) continue;
            int max = needle.length() >= 7 ? 2 : 1;
            if (editDistanceAtMost(needle, token, max)) return true;
        }
        return false;
    }

    private static boolean editDistanceAtMost(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) return false;
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int val = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                cur[j] = val;
                if (val < rowMin) rowMin = val;
            }
            if (rowMin > max) return false;
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()] <= max;
    }

    private static double recency(ScreenshotItem item, long nowMs) {
        if (item.takenAt <= 0 || nowMs <= 0) return 0;
        long ageDays = Math.max(0, (nowMs - item.takenAt) / 86400000L);
        if (ageDays <= 1) return 8;
        if (ageDays <= 7) return 5;
        if (ageDays <= 30) return 3;
        if (ageDays <= 180) return 1;
        return 0;
    }
}
