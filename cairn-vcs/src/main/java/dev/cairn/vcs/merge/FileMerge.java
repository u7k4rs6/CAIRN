package dev.cairn.vcs.merge;

import dev.cairn.vcs.diff.Edit;
import dev.cairn.vcs.diff.EditType;
import dev.cairn.vcs.diff.MyersDiff;

import java.util.ArrayList;
import java.util.List;

/**
 * Three-way line merge of a single file: compares base against ours and against
 * theirs (each via {@link MyersDiff}), then merges those two edit scripts against
 * each other, the way {@code diff3} does. Non-overlapping regions merge automatically;
 * a region both sides touched differently becomes one conflict hunk.
 *
 * <p><b>Conflict granularity.</b> Detection is per contiguous changed region, not
 * whole-file, so unrelated edits to the same file merge cleanly and only the
 * overlapping region is reported as a conflict (architecture doc, section 4.6).
 *
 * <p><b>Documented limitation.</b> Two changes that touch immediately adjacent but
 * non-overlapping base lines are merged into a single conflict-candidate region if a
 * change from the other side bridges them (the same behavior {@code diff3} has);
 * this trades a slightly wider conflict region for a simpler, provably terminating
 * merge loop rather than trying to split partially-overlapping regions further.
 */
public final class FileMerge {

    private final MyersDiff<String> diff = new MyersDiff<>();

    public MergeResult merge(String path, List<String> base, List<String> ours, List<String> theirs) {
        List<Span> spansA = mergeAdjacent(spansFrom(diff.diff(base, ours), ours));
        List<Span> spansB = mergeAdjacent(spansFrom(diff.diff(base, theirs), theirs));

        List<String> merged = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        int basePos = 0;
        int ai = 0;
        int bi = 0;

        while (ai < spansA.size() || bi < spansB.size()) {
            Span nextA = ai < spansA.size() ? spansA.get(ai) : null;
            Span nextB = bi < spansB.size() ? spansB.get(bi) : null;

            if (nextA != null && (nextB == null || nextA.baseEnd() <= nextB.baseStart())) {
                merged.addAll(base.subList(basePos, nextA.baseStart()));
                merged.addAll(nextA.replacement());
                basePos = nextA.baseEnd();
                ai++;
                continue;
            }
            if (nextB != null && (nextA == null || nextB.baseEnd() <= nextA.baseStart())) {
                merged.addAll(base.subList(basePos, nextB.baseStart()));
                merged.addAll(nextB.replacement());
                basePos = nextB.baseEnd();
                bi++;
                continue;
            }

            // Overlap: absorb every span from either side transitively connected to
            // this region before resolving it, so a chain of touching A/B spans
            // becomes one merge unit instead of several.
            int regionStart = Math.min(nextA.baseStart(), nextB.baseStart());
            int regionEnd = Math.max(nextA.baseEnd(), nextB.baseEnd());
            int regionAStart = ai;
            int regionBStart = bi;
            boolean expanded = true;
            while (expanded) {
                expanded = false;
                while (ai < spansA.size() && spansA.get(ai).baseStart() < regionEnd) {
                    regionEnd = Math.max(regionEnd, spansA.get(ai).baseEnd());
                    ai++;
                    expanded = true;
                }
                while (bi < spansB.size() && spansB.get(bi).baseStart() < regionEnd) {
                    regionEnd = Math.max(regionEnd, spansB.get(bi).baseEnd());
                    bi++;
                    expanded = true;
                }
            }

            List<String> oursText = project(base, spansA.subList(regionAStart, ai), regionStart, regionEnd);
            List<String> theirsText = project(base, spansB.subList(regionBStart, bi), regionStart, regionEnd);

            merged.addAll(base.subList(basePos, regionStart));
            if (oursText.equals(theirsText)) {
                merged.addAll(oursText);
            } else {
                conflicts.add(new Conflict(path, base.subList(regionStart, regionEnd), oursText, theirsText));
                merged.addAll(oursText);
            }
            basePos = regionEnd;
        }
        merged.addAll(base.subList(basePos, base.size()));

        return new MergeResult(merged, conflicts);
    }

    /** Reconstructs one side's text for {@code [regionStart, regionEnd)} of base, replaying that side's own spans within it. */
    private List<String> project(List<String> base, List<Span> sideSpans, int regionStart, int regionEnd) {
        List<String> result = new ArrayList<>();
        int pos = regionStart;
        for (Span span : sideSpans) {
            result.addAll(base.subList(pos, span.baseStart()));
            result.addAll(span.replacement());
            pos = span.baseEnd();
        }
        result.addAll(base.subList(pos, regionEnd));
        return result;
    }

    private List<Span> spansFrom(List<Edit> edits, List<String> revised) {
        List<Span> spans = new ArrayList<>();
        for (Edit edit : edits) {
            if (edit.type() == EditType.EQUAL) {
                continue;
            }
            List<String> replacement = edit.type() == EditType.INSERT
                    ? revised.subList(edit.revStart(), edit.revEnd())
                    : List.of();
            spans.add(new Span(edit.origStart(), edit.origEnd(), replacement));
        }
        return spans;
    }

    /** Merges spans touching end-to-end (a delete immediately followed by an insert at the same base point, i.e. a "modify"). */
    private List<Span> mergeAdjacent(List<Span> spans) {
        List<Span> result = new ArrayList<>();
        for (Span s : spans) {
            if (!result.isEmpty() && result.get(result.size() - 1).baseEnd() == s.baseStart()) {
                Span prev = result.remove(result.size() - 1);
                List<String> combined = new ArrayList<>(prev.replacement());
                combined.addAll(s.replacement());
                result.add(new Span(prev.baseStart(), s.baseEnd(), combined));
            } else {
                result.add(s);
            }
        }
        return result;
    }

    private record Span(int baseStart, int baseEnd, List<String> replacement) {
    }

    /** The outcome of merging one file: the merged lines, and any conflicts within it. */
    public record MergeResult(List<String> lines, List<Conflict> conflicts) {
        public boolean isClean() {
            return conflicts.isEmpty();
        }
    }
}
