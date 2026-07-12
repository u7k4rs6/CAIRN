package dev.cairn.vcs.merge;

import dev.cairn.vcs.diff.Lines;
import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.store.ObjectStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Three-way merge of two trees against a common base tree: for each path, decide
 * whether only one side changed (take it), both sides made the identical change
 * (take it, no conflict), or both changed it differently (a conflict).
 *
 * <p><b>Conflict cases.</b> A per-file conflict is either a hunk-level conflict from
 * {@link FileMerge} (both sides modified the same file, differently, in overlapping
 * regions) or a whole-file conflict this class detects directly: both sides added
 * the path with different content, or one side modified it while the other deleted
 * it. The result always contains a best-effort tree even when conflicts remain
 * (mirroring a real merge leaving conflict markers rather than aborting), so callers
 * can inspect {@link Outcome#conflicts()} to decide whether to accept it.
 *
 * <p><b>Documented limitation.</b> No rename detection: a file deleted on one side and
 * a similarly-named-and-shaped file added on the other are treated as an unrelated
 * delete and an unrelated add, not a move (architecture doc, section 4.6).
 */
public final class TreeMerger {

    private final ObjectStore store;
    private final FileMerge fileMerge = new FileMerge();

    public TreeMerger(ObjectStore store) {
        this.store = store;
    }

    public record Outcome(ObjectId treeId, List<Conflict> conflicts) {
        public boolean isClean() {
            return conflicts.isEmpty();
        }
    }

    public Outcome merge(ObjectId baseTree, ObjectId oursTree, ObjectId theirsTree) {
        Map<String, BlobEntry> baseMap = TreeFlattener.flatten(store, baseTree);
        Map<String, BlobEntry> oursMap = TreeFlattener.flatten(store, oursTree);
        Map<String, BlobEntry> theirsMap = TreeFlattener.flatten(store, theirsTree);

        var paths = new TreeSet<String>();
        paths.addAll(baseMap.keySet());
        paths.addAll(oursMap.keySet());
        paths.addAll(theirsMap.keySet());

        Map<String, BlobEntry> result = new TreeMap<>();
        List<Conflict> conflicts = new ArrayList<>();

        for (String path : paths) {
            BlobEntry baseE = baseMap.get(path);
            BlobEntry oursE = oursMap.get(path);
            BlobEntry theirsE = theirsMap.get(path);
            ObjectId baseId = baseE == null ? null : baseE.id();
            ObjectId oursId = oursE == null ? null : oursE.id();
            ObjectId theirsId = theirsE == null ? null : theirsE.id();

            if (Objects.equals(oursId, theirsId)) {
                if (oursE != null) {
                    result.put(path, oursE);
                }
                continue;
            }
            if (Objects.equals(oursId, baseId)) {
                if (theirsE != null) {
                    result.put(path, theirsE);
                }
                continue;
            }
            if (Objects.equals(theirsId, baseId)) {
                if (oursE != null) {
                    result.put(path, oursE);
                }
                continue;
            }

            if (baseE != null && oursE != null && theirsE != null) {
                var merged = fileMerge.merge(path, linesOf(baseE), linesOf(oursE), linesOf(theirsE));
                ObjectId mergedBlob = store.put(new Blob(Lines.join(merged.lines())));
                result.put(path, new BlobEntry(oursE.mode(), mergedBlob));
                conflicts.addAll(merged.conflicts());
            } else if (baseE == null && oursE != null) {
                // add/add with different content: not a hunk conflict, a whole-file one.
                conflicts.add(new Conflict(path, List.of(), linesOf(oursE), linesOf(theirsE)));
                result.put(path, oursE);
            } else {
                // modify/delete in one direction or the other.
                List<String> baseLines = baseE != null ? linesOf(baseE) : List.of();
                List<String> oursLines = oursE != null ? linesOf(oursE) : List.of();
                List<String> theirsLines = theirsE != null ? linesOf(theirsE) : List.of();
                conflicts.add(new Conflict(path, baseLines, oursLines, theirsLines));
                if (oursE != null) {
                    result.put(path, oursE);
                } else if (theirsE != null) {
                    result.put(path, theirsE);
                }
            }
        }

        ObjectId mergedTree = TreeFlattener.build(store, result);
        return new Outcome(mergedTree, conflicts);
    }

    private List<String> linesOf(BlobEntry entry) {
        Blob blob = (Blob) store.get(entry.id()).orElseThrow();
        return Lines.of(blob.content());
    }
}
