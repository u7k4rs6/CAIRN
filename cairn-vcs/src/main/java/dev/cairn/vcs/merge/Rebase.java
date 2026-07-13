package dev.cairn.vcs.merge;

import dev.cairn.vcs.dag.Ancestry;
import dev.cairn.vcs.dag.GenerationStore;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.store.ObjectStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The {@code REBASE} merge strategy (architecture doc's named strategies): replays
 * each commit unique to {@code source} onto {@code target}'s current tip, one at a
 * time, rather than {@link MergeEngine}'s single three-way merge producing one
 * merge commit. Named as a gap in the first build (see {@code DECISIONS.md}, M7)
 * because it needs real per-commit replay machinery beyond "compute one merged
 * tree, write one commit," which is what this class is.
 *
 * <p><b>Algorithm.</b> Find the merge base of target and source, then walk source's
 * first-parent chain back to that base to get the commits to replay, oldest first.
 * Replay each one by three-way merging (its own first parent's tree as base, the
 * rebase's growing tip as ours, its own tree as theirs) onto the previous replayed
 * commit, preserving the original author but stamping a new committer identity and
 * time, exactly as real Git rebase does.
 *
 * <p><b>Documented limitations,</b> the same shape as this project's existing
 * merge/blame scope boundaries: only the first-parent chain is replayed, so a merge
 * commit inside the range being rebased is flattened into one ordinary commit
 * capturing its net change rather than preserved as a merge (real Git needs
 * {@code --rebase-merges} to do otherwise); and this implementation is all-or-
 * nothing, since there is no working directory on the server to pause in and show
 * conflict markers for. A conflict at any replayed commit aborts the whole rebase
 * with no partial history written, rather than the interactive pause-resume-skip
 * real Git offers.
 */
public final class Rebase {

    private final ObjectStore store;
    private final Ancestry ancestry;
    private final TreeMerger treeMerger;

    public Rebase(ObjectStore store, GenerationStore generations) {
        this.store = store;
        this.ancestry = new Ancestry(store, generations);
        this.treeMerger = new TreeMerger(store);
    }

    public record Outcome(boolean alreadyUpToDate, ObjectId newTip, List<Conflict> conflicts,
                           ObjectId conflictingSourceCommit) {
        public boolean isClean() {
            return conflicts.isEmpty();
        }
    }

    public Outcome rebase(ObjectId target, ObjectId source, PersonIdent committer) {
        if (source.equals(target) || ancestry.isAncestor(source, target)) {
            return new Outcome(true, target, List.of(), null);
        }

        ObjectId base = mergeBaseOf(target, source);
        List<ObjectId> chain = firstParentChainSince(source, base);

        ObjectId tip = target;
        for (ObjectId commitId : chain) {
            Commit original = load(commitId);
            ObjectId parentTree = original.parents().isEmpty() ? emptyTree() : load(original.parents().get(0)).treeId();
            ObjectId oursTree = load(tip).treeId();
            TreeMerger.Outcome merged = treeMerger.merge(parentTree, oursTree, original.treeId());
            if (!merged.isClean()) {
                return new Outcome(false, target, merged.conflicts(), commitId);
            }
            Commit replayed = new Commit(merged.treeId(), List.of(tip), original.author(), committer, original.message());
            tip = store.put(replayed);
        }
        return new Outcome(false, tip, List.of(), null);
    }

    private ObjectId mergeBaseOf(ObjectId target, ObjectId source) {
        List<ObjectId> bases = ancestry.mergeBases(target, source);
        return bases.isEmpty() ? null : bases.get(0);
    }

    /** {@code source}'s own first-parent ancestors down to (excluding) {@code base}, oldest first; walks to a root if {@code base} is null or never found (unrelated histories). */
    private List<ObjectId> firstParentChainSince(ObjectId source, ObjectId base) {
        List<ObjectId> chain = new ArrayList<>();
        ObjectId cursor = source;
        while (cursor != null && !cursor.equals(base)) {
            chain.add(cursor);
            Commit commit = load(cursor);
            cursor = commit.parents().isEmpty() ? null : commit.parents().get(0);
        }
        Collections.reverse(chain);
        return chain;
    }

    private Commit load(ObjectId id) {
        return (Commit) store.get(id).orElseThrow(() -> new IllegalArgumentException("not a commit: " + id));
    }

    private ObjectId emptyTree() {
        return store.put(new Tree(List.of()));
    }
}
