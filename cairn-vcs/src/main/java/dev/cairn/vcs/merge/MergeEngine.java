package dev.cairn.vcs.merge;

import dev.cairn.vcs.dag.Ancestry;
import dev.cairn.vcs.dag.GenerationStore;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.store.ObjectStore;

import java.util.List;

/**
 * Top-level merge orchestration: fast-forward detection, merge-base resolution
 * (including the recursive strategy for criss-cross history), and the resulting
 * tree-level three-way merge. This is the type porcelain {@code merge} calls.
 */
public final class MergeEngine {

    private final ObjectStore store;
    private final Ancestry ancestry;
    private final TreeMerger treeMerger;

    public MergeEngine(ObjectStore store, GenerationStore generations) {
        this.store = store;
        this.ancestry = new Ancestry(store, generations);
        this.treeMerger = new TreeMerger(store);
    }

    /** The result of attempting to merge {@code theirs} into {@code ours}. */
    public record Outcome(boolean alreadyUpToDate, boolean fastForward, ObjectId mergedTreeId, List<Conflict> conflicts) {
        public boolean isClean() {
            return conflicts.isEmpty();
        }
    }

    public Outcome merge(ObjectId ours, ObjectId theirs) {
        if (ours.equals(theirs) || ancestry.isAncestor(theirs, ours)) {
            return new Outcome(true, false, commitTree(ours), List.of());
        }
        if (ancestry.isAncestor(ours, theirs)) {
            return new Outcome(false, true, commitTree(theirs), List.of());
        }
        ObjectId baseTree = resolveBaseTree(ours, theirs);
        TreeMerger.Outcome outcome = treeMerger.merge(baseTree, commitTree(ours), commitTree(theirs));
        return new Outcome(false, false, outcome.treeId(), outcome.conflicts());
    }

    /**
     * The tree to treat as "base" when three-way-merging {@code a} and {@code b}.
     * With one merge base, that commit's own tree. With several (criss-cross
     * history), the recursive strategy: merge the bases together first (recursing,
     * since they may themselves have multiple bases) to synthesize a single virtual
     * tree, then use that.
     *
     * <p><b>Documented limitation.</b> Folding more than two merge bases reuses the
     * virtual tree from the first fold as the "base" for absorbing each additional
     * base, rather than finding a proper recursive base for it (which would require
     * a real commit id, and the virtual tree is not one). Three or more merge bases
     * is already a rare criss-cross shape; this keeps the fold total and terminating
     * rather than attempting a fully general N-way recursive base.
     */
    ObjectId resolveBaseTree(ObjectId a, ObjectId b) {
        List<ObjectId> bases = ancestry.mergeBases(a, b);
        if (bases.isEmpty()) {
            return emptyTree();
        }
        if (bases.size() == 1) {
            return commitTree(bases.get(0));
        }
        ObjectId accumulatorCommit = bases.get(0);
        ObjectId accumulatorTree = commitTree(accumulatorCommit);
        for (int i = 1; i < bases.size(); i++) {
            ObjectId nextCommit = bases.get(i);
            ObjectId nextTree = commitTree(nextCommit);
            ObjectId subBase = accumulatorCommit != null
                    ? resolveBaseTree(accumulatorCommit, nextCommit)
                    : accumulatorTree;
            accumulatorTree = treeMerger.merge(subBase, accumulatorTree, nextTree).treeId();
            accumulatorCommit = null;
        }
        return accumulatorTree;
    }

    private ObjectId commitTree(ObjectId commitId) {
        Commit commit = (Commit) store.get(commitId)
                .orElseThrow(() -> new IllegalArgumentException("not a commit: " + commitId));
        return commit.treeId();
    }

    private ObjectId emptyTree() {
        return store.put(new Tree(List.of()));
    }
}
