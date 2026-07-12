package dev.cairn.vcs.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Myers' diff algorithm (An O(ND) Difference Algorithm and Its Variations, 1986),
 * with the linear-space refinement: instead of recording every furthest-reaching
 * path for traceback (O(ND) space), it finds only the middle snake of the edit
 * graph and recurses on the two halves it splits the problem into (the Hirschberg
 * idea applied to the edit graph).
 *
 * <p><b>Complexity.</b> Time is O(N D), where N is the combined sequence length and D
 * is the edit distance: fast when the inputs are similar (D small), degrading toward
 * O(N^2) when they are almost entirely different. Space is O(N): the two furthest-
 * reaching-path arrays used to find each middle snake are O(N) each and are discarded
 * once that snake is found, rather than kept for the whole recursion. The naive
 * dynamic-programming table this replaces is O(N^2) time and space; the histogram and
 * patience alternatives (not implemented as strategies here, named as the documented
 * alternative) anchor on lines unique to both sides and produce more human-readable
 * diffs at the cost of not guaranteeing a minimal edit script.
 */
public final class MyersDiff<T> implements DiffStrategy<T> {

    @Override
    public List<Edit> diff(List<T> orig, List<T> rev) {
        List<Edit> raw = new ArrayList<>();
        compute(orig, 0, orig.size(), rev, 0, rev.size(), raw);
        return coalesce(raw);
    }

    private void compute(List<T> a, int aLo, int aHi, List<T> b, int bLo, int bHi, List<Edit> out) {
        // Trim a shared prefix and suffix first: this shrinks N and D for the
        // recursive step and sidesteps degenerate middle-snake cases where one side
        // is already empty. The trimmed regions are matches too, so unlike the
        // interior recursion they must be emitted here directly rather than via a
        // middle snake.
        int prefixALo = aLo;
        int prefixBLo = bLo;
        while (aLo < aHi && bLo < bHi && Objects.equals(a.get(aLo), b.get(bLo))) {
            aLo++;
            bLo++;
        }
        if (aLo > prefixALo) {
            out.add(new Edit(EditType.EQUAL, prefixALo, aLo, prefixBLo, bLo));
        }

        int aHiTrim = aHi;
        int bHiTrim = bHi;
        while (aHiTrim > aLo && bHiTrim > bLo && Objects.equals(a.get(aHiTrim - 1), b.get(bHiTrim - 1))) {
            aHiTrim--;
            bHiTrim--;
        }
        boolean hasSuffix = aHiTrim < aHi;

        if (aLo == aHiTrim && bLo == bHiTrim) {
            // core is empty: nothing between the trimmed prefix and suffix
        } else if (aLo == aHiTrim) {
            emitInsert(out, aLo, bLo, bHiTrim);
        } else if (bLo == bHiTrim) {
            emitDelete(out, aLo, aHiTrim, bLo);
        } else {
            Snake snake = middleSnake(a, aLo, aHiTrim, b, bLo, bHiTrim);
            compute(a, aLo, snake.aStart(), b, bLo, snake.bStart(), out);
            if (snake.aEnd() > snake.aStart()) {
                out.add(new Edit(EditType.EQUAL, snake.aStart(), snake.aEnd(), snake.bStart(), snake.bEnd()));
            }
            compute(a, snake.aEnd(), aHiTrim, b, snake.bEnd(), bHiTrim, out);
        }

        if (hasSuffix) {
            out.add(new Edit(EditType.EQUAL, aHiTrim, aHi, bHiTrim, bHi));
        }
    }

    private void emitInsert(List<Edit> out, int aLo, int bLo, int bHi) {
        if (bLo < bHi) {
            out.add(new Edit(EditType.INSERT, aLo, aLo, bLo, bHi));
        }
    }

    private void emitDelete(List<Edit> out, int aLo, int aHi, int bLo) {
        if (aLo < aHi) {
            out.add(new Edit(EditType.DELETE, aLo, aHi, bLo, bLo));
        }
    }

    private record Snake(int aStart, int bStart, int aEnd, int bEnd) {
    }

    /**
     * Finds the middle snake of the edit graph for the window {@code a[aLo,aHi)} vs
     * {@code b[bLo,bHi)}: the diagonal run of matches that a minimal edit script must
     * pass through, found by growing forward-reaching and backward-reaching frontiers
     * until they overlap (Myers, section 4b).
     */
    private Snake middleSnake(List<T> a, int aLo, int aHi, List<T> b, int bLo, int bHi) {
        int n = aHi - aLo;
        int m = bHi - bLo;
        int max = n + m;
        int[] vf = new int[2 * max + 3];
        int[] vb = new int[2 * max + 3];
        int offset = max + 1;
        vf[1 + offset] = 0;
        vb[1 + offset] = 0;
        int delta = n - m;
        boolean deltaOdd = (delta & 1) != 0;
        // D (the edit distance for this window) can never exceed n + m: deleting
        // everything from a and inserting everything from b is always a valid path.
        // The forward/backward frontiers are guaranteed to overlap at or before that
        // bound, so it is a safe (if generous) loop limit; the overlap check below is
        // what actually stops the search as soon as a snake is found, in practice far
        // earlier than this bound for any two sequences that share structure.
        int dMax = max;

        for (int d = 0; d <= dMax; d++) {
            for (int k = -d; k <= d; k += 2) {
                int idx = k + offset;
                int x;
                if (k == -d || (k != d && vf[idx - 1] < vf[idx + 1])) {
                    x = vf[idx + 1];
                } else {
                    x = vf[idx - 1] + 1;
                }
                int y = x - k;
                int xStart = x;
                int yStart = y;
                while (x < n && y < m && Objects.equals(a.get(aLo + x), b.get(bLo + y))) {
                    x++;
                    y++;
                }
                vf[idx] = x;
                if (deltaOdd) {
                    // A forward point on diagonal k corresponds to reversed-coordinate
                    // diagonal (delta - k): reversed diagonal is (N-x)-(M-y) = delta - k.
                    int kb = delta - k;
                    if (kb >= -(d - 1) && kb <= (d - 1)) {
                        int idxB = kb + offset;
                        int bx = vb[idxB];
                        if (n - bx <= x) {
                            return new Snake(aLo + xStart, bLo + yStart, aLo + x, bLo + y);
                        }
                    }
                }
            }
            for (int k = -d; k <= d; k += 2) {
                int idx = k + offset;
                int x;
                if (k == -d || (k != d && vb[idx - 1] < vb[idx + 1])) {
                    x = vb[idx + 1];
                } else {
                    x = vb[idx - 1] + 1;
                }
                int y = x - k;
                int xStart = x;
                int yStart = y;
                while (x < n && y < m && Objects.equals(a.get(aHi - 1 - x), b.get(bHi - 1 - y))) {
                    x++;
                    y++;
                }
                vb[idx] = x;
                if (!deltaOdd) {
                    // Same correspondence, applied the other direction: a backward
                    // point on reversed diagonal k corresponds to forward diagonal (delta - k).
                    int kf = delta - k;
                    if (kf >= -d && kf <= d) {
                        int idxF = kf + offset;
                        int fx = vf[idxF];
                        if (n - x <= fx) {
                            return new Snake(aLo + n - x, bLo + m - y, aLo + n - xStart, bLo + m - yStart);
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("middle snake not found; this indicates a bug, not a valid input");
    }

    /** Merges adjacent edits of the same type into one range, and sorts by position. */
    private List<Edit> coalesce(List<Edit> edits) {
        edits.sort((x, y) -> {
            int cmp = Integer.compare(x.origStart(), y.origStart());
            return cmp != 0 ? cmp : Integer.compare(x.revStart(), y.revStart());
        });
        List<Edit> result = new ArrayList<>();
        for (Edit edit : edits) {
            if (!result.isEmpty()) {
                Edit last = result.get(result.size() - 1);
                if (last.type() == edit.type() && last.origEnd() == edit.origStart() && last.revEnd() == edit.revStart()) {
                    result.set(result.size() - 1,
                            new Edit(last.type(), last.origStart(), edit.origEnd(), last.revStart(), edit.revEnd()));
                    continue;
                }
            }
            result.add(edit);
        }
        return result;
    }
}
