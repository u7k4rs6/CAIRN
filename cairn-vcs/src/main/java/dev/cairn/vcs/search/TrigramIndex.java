package dev.cairn.vcs.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * FR-SEARCH-1: an inverted trigram index over document content, so a substring
 * query is answered by intersecting posting lists and verifying a small candidate
 * set, rather than scanning every document (architecture doc, section 10).
 *
 * <p><b>Complexity and tradeoff.</b> Building the index is O(total content length):
 * every document contributes one posting-list entry per overlapping 3-character
 * window. A query of length L extracts O(L) trigrams; intersecting their posting
 * lists costs O(sum of the smallest posting lists' sizes) rather than O(document
 * count), and each surviving candidate is then verified against its actual content
 * (cheap, since trigram membership alone does not prove the query substring
 * actually occurs contiguously and in order: three documents each containing "abc"
 * and "bcd" as trigrams need not contain "abcd" as a substring, which is why
 * candidates are always re-checked, never returned on trigram membership alone).
 * This is near O(intersected postings + candidate verification) per query instead
 * of an O(total content) grep, at the cost of holding every document's content and
 * postings in memory (no on-disk index, no incremental update on a single document
 * change; the whole index is rebuilt wholesale, which is the tradeoff
 * {@code RepoSearchIndexService} in cairn-api makes explicit for staleness on push).
 */
public final class TrigramIndex {

    private final Map<String, Set<String>> postings = new HashMap<>();
    private final Map<String, String> contentByPath = new HashMap<>();

    /** Indexes one document's content under {@code path}, which doubles as its unique key. */
    public void add(String path, String content) {
        contentByPath.put(path, content);
        for (String trigram : trigramsOf(content.toLowerCase(Locale.ROOT))) {
            postings.computeIfAbsent(trigram, t -> new HashSet<>()).add(path);
        }
    }

    public int documentCount() {
        return contentByPath.size();
    }

    /** A single matched line within a file. */
    public record LineMatch(int lineNumber, String line) {
    }

    /** Every matched line in one file, grouped for a {@code ResultGroup}-shaped UI (frontend spec, section 5.8). */
    public record FileMatch(String path, List<LineMatch> lines) {
    }

    /**
     * Trigram matching needs at least three characters to extract a single trigram
     * (frontend spec, section 5.8's "query too short" state); shorter queries
     * always return empty rather than degrading to a full scan.
     */
    public List<FileMatch> search(String query) {
        if (query == null || query.length() < 3) {
            return List.of();
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        Set<String> candidates = candidateDocuments(lowerQuery);

        List<FileMatch> results = new ArrayList<>();
        for (String path : candidates) {
            String content = contentByPath.get(path);
            List<LineMatch> lines = matchingLines(content, lowerQuery);
            if (!lines.isEmpty()) {
                results.add(new FileMatch(path, lines));
            }
        }
        results.sort((a, b) -> a.path().compareTo(b.path()));
        return results;
    }

    private Set<String> candidateDocuments(String lowerQuery) {
        Set<String> candidates = null;
        for (String trigram : trigramsOf(lowerQuery)) {
            Set<String> docs = postings.getOrDefault(trigram, Set.of());
            if (candidates == null) {
                candidates = new HashSet<>(docs);
            } else {
                candidates.retainAll(docs);
            }
            if (candidates.isEmpty()) {
                return Set.of();
            }
        }
        return candidates == null ? Set.of() : candidates;
    }

    private static List<LineMatch> matchingLines(String content, String lowerQuery) {
        List<LineMatch> lines = new ArrayList<>();
        String[] split = content.split("\n", -1);
        for (int i = 0; i < split.length; i++) {
            if (split[i].toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                lines.add(new LineMatch(i + 1, split[i]));
            }
        }
        return lines;
    }

    static Set<String> trigramsOf(String s) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i + 3 <= s.length(); i++) {
            out.add(s.substring(i, i + 3));
        }
        return out;
    }
}
