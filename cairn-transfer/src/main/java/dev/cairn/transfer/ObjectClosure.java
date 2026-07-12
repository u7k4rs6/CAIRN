package dev.cairn.transfer;

import dev.cairn.vcs.dag.RevWalk;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.store.ObjectStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Computes the full set of objects (commits, trees, and blobs, not just commits)
 * reachable from a set of starting commits: what {@code reachable_from(...)} means
 * in the negotiation formula {@code missing = reachable_from(wants) \ reachable_from(haves)}
 * (architecture doc, section 8). {@link RevWalk} alone only walks the commit graph;
 * this layers a tree walk on top so the result is the actual set of objects a pack
 * needs to contain.
 */
final class ObjectClosure {

    private ObjectClosure() {
    }

    /** Oldest-commit-first insertion order, so a pack built from this set tends to place related blob revisions near each other. */
    static LinkedHashSet<ObjectId> from(ObjectStore store, List<ObjectId> startCommits) {
        RevWalk walk = new RevWalk(store);
        List<Commit> commits = walk.history(startCommits);
        List<Commit> oldestFirst = new ArrayList<>(commits);
        Collections.reverse(oldestFirst);

        LinkedHashSet<ObjectId> result = new LinkedHashSet<>();
        for (Commit commit : oldestFirst) {
            result.add(commit.id());
        }
        for (Commit commit : oldestFirst) {
            walkTree(store, commit.treeId(), result);
        }
        return result;
    }

    private static void walkTree(ObjectStore store, ObjectId treeId, LinkedHashSet<ObjectId> out) {
        if (!out.add(treeId)) {
            return;
        }
        Tree tree = (Tree) store.get(treeId).orElseThrow(() -> new IllegalStateException("missing tree " + treeId));
        for (TreeEntry entry : tree.entries()) {
            if (entry.isDirectory()) {
                walkTree(store, entry.id(), out);
            } else {
                out.add(entry.id());
            }
        }
    }
}
