package dev.cairn.vcs.dag;

import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.ref.RefStore;
import dev.cairn.vcs.store.ObjectStore;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalInt;

/**
 * Computes and persists generation numbers: {@code gen(commit) = 1 + max(gen(parent))},
 * roots at 1.
 *
 * <p><b>Complexity.</b> A full recompute from a set of starting refs is a single
 * topological pass, O(V + E) over the reachable graph, computed once and persisted
 * (architecture doc, section 4.4). Assigning a single newly-created commit is O(1)
 * amortized: its parents are the commits it was built from, which (in normal
 * porcelain use) were committed earlier and so already have a stored generation;
 * the recursive fallback below only does real work the first time a repository's
 * pre-existing history is encountered without generations yet computed (for example,
 * right after receiving history over transfer).
 */
public final class GenerationNumbers {

    private GenerationNumbers() {
    }

    /** Assigns (and persists) the generation for a single commit, computing any missing ancestor generations along the way. */
    public static int computeAndStore(ObjectStore store, GenerationStore generations, ObjectId commitId) {
        OptionalInt existing = generations.get(commitId);
        if (existing.isPresent()) {
            return existing.getAsInt();
        }
        Commit commit = (Commit) store.get(commitId)
                .orElseThrow(() -> new IllegalArgumentException("not a commit: " + commitId));
        int gen = 1;
        for (ObjectId parent : commit.parents()) {
            gen = Math.max(gen, 1 + computeAndStore(store, generations, parent));
        }
        generations.put(commitId, gen);
        return gen;
    }

    /** Recomputes generations for every commit reachable from every ref, in one topological pass. Used for bulk (re)population. */
    public static void recomputeAll(ObjectStore store, RefStore refs, GenerationStore generations) {
        Deque<ObjectId> stack = new ArrayDeque<>(refs.list().values());
        while (!stack.isEmpty()) {
            ObjectId id = stack.pop();
            if (generations.get(id).isPresent()) {
                continue;
            }
            Commit commit = (Commit) store.get(id).orElseThrow();
            boolean allParentsReady = true;
            for (ObjectId parent : commit.parents()) {
                if (generations.get(parent).isEmpty()) {
                    if (allParentsReady) {
                        stack.push(id);
                    }
                    stack.push(parent);
                    allParentsReady = false;
                }
            }
            if (allParentsReady) {
                computeAndStore(store, generations, id);
            }
        }
    }
}
