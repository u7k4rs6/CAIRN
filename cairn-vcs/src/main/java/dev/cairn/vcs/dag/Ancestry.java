package dev.cairn.vcs.dag;

import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.store.ObjectStore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ancestry queries over the commit DAG: "is A an ancestor of B", and the merge-base
 * (lowest common ancestor, possibly more than one under criss-cross history).
 *
 * <p><b>Complexity and honest framing.</b> Generation numbers do not change the
 * pathological worst case (architecture doc, section 4.4): {@link #mergeBases} still
 * falls back to a full O(V + E) reachable-set computation whenever neither side is a
 * direct ancestor of the other, because a lowest common ancestor in an adversarial
 * graph is not guaranteed to be found by any generation-bounded frontier search
 * without additional bookkeeping (stale-marking) this implementation does not
 * attempt. What generation numbers reliably buy, and what is implemented and tested
 * here, are exactly the two cases the architecture doc names: an O(1) negative
 * answer for {@link #isAncestor}, and a pruned positive walk that cuts any branch
 * whose generation has already fallen below the candidate's. Both of those also
 * accelerate the extremely common real-world case where a merge is a fast-forward
 * or one side is already an ancestor of the other, since {@link #mergeBases} checks
 * that first before ever falling back to full enumeration.
 */
public final class Ancestry {

    private final ObjectStore store;
    private final RevWalk revWalk;
    private final GenerationStore generations;

    public Ancestry(ObjectStore store, GenerationStore generations) {
        this.store = store;
        this.revWalk = new RevWalk(store);
        this.generations = generations;
    }

    private int generationOf(ObjectId id) {
        return generations.get(id).orElseGet(() -> GenerationNumbers.computeAndStore(store, generations, id));
    }

    /**
     * Is {@code candidate} an ancestor of {@code of}? O(1) when it structurally
     * cannot be (candidate's generation is not lower), since an ancestor's
     * generation is always strictly less than its descendant's. Otherwise a walk
     * from {@code of} that never enqueues a parent whose generation has already
     * dropped below {@code candidate}'s, since no path through it could reach back
     * up to candidate.
     */
    public boolean isAncestor(ObjectId candidate, ObjectId of) {
        if (candidate.equals(of)) {
            return false;
        }
        int candidateGen = generationOf(candidate);
        if (generationOf(of) <= candidateGen) {
            return false;
        }
        Deque<ObjectId> stack = new ArrayDeque<>();
        Set<ObjectId> visited = new HashSet<>();
        stack.push(of);
        while (!stack.isEmpty()) {
            ObjectId id = stack.pop();
            if (!visited.add(id)) {
                continue;
            }
            if (id.equals(candidate)) {
                return true;
            }
            Commit commit = (Commit) store.get(id).orElseThrow();
            for (ObjectId parent : commit.parents()) {
                if (generationOf(parent) >= candidateGen) {
                    stack.push(parent);
                }
            }
        }
        return false;
    }

    /**
     * The lowest common ancestors of {@code a} and {@code b}: common ancestors that
     * are not themselves an ancestor of another common ancestor. Empty if the
     * histories share no ancestor; more than one entry only under criss-cross history
     * (architecture doc, section 4.6).
     */
    public List<ObjectId> mergeBases(ObjectId a, ObjectId b) {
        if (a.equals(b) || isAncestor(b, a)) {
            return List.of(b);
        }
        if (isAncestor(a, b)) {
            return List.of(a);
        }

        Set<ObjectId> reachA = revWalk.reachableFrom(a);
        Set<ObjectId> reachB = revWalk.reachableFrom(b);
        Set<ObjectId> common = new HashSet<>(reachA);
        common.retainAll(reachB);
        if (common.isEmpty()) {
            return List.of();
        }
        List<ObjectId> candidates = new ArrayList<>(common);
        List<ObjectId> result = new ArrayList<>();
        for (ObjectId candidate : candidates) {
            boolean dominated = false;
            for (ObjectId other : candidates) {
                if (!other.equals(candidate) && isAncestor(candidate, other)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                result.add(candidate);
            }
        }
        return result;
    }
}
