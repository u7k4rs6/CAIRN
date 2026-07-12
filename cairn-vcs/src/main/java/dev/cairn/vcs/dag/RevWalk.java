package dev.cairn.vcs.dag;

import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.store.ObjectStore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Walks the commit DAG from one or more starting commits, visiting each reachable
 * commit exactly once.
 *
 * <p><b>Complexity.</b> Naive walking is O(V + E) over the commits and parent edges
 * reachable from the starting points (architecture doc, section 4.4): every ancestor
 * may need visiting once the walk isn't accelerated. This class is the naive walker;
 * {@link Ancestry} layers generation-number pruning on top of the same underlying
 * traversal for the ancestry and merge-base queries that can afford to stop early.
 */
public final class RevWalk {

    private final ObjectStore store;

    public RevWalk(ObjectStore store) {
        this.store = store;
    }

    public Commit load(ObjectId id) {
        return store.get(id)
                .filter(o -> o instanceof Commit)
                .map(o -> (Commit) o)
                .orElseThrow(() -> new IllegalArgumentException("not a commit: " + id));
    }

    /** All commits reachable from {@code starts}, newest committer-time first. */
    public List<Commit> history(List<ObjectId> starts) {
        List<Commit> result = new ArrayList<>();
        PriorityQueue<Commit> queue = new PriorityQueue<>(
                Comparator.comparingLong((Commit c) -> c.committer().epochSeconds()).reversed());
        Set<ObjectId> seen = new HashSet<>();
        for (ObjectId start : starts) {
            if (seen.add(start)) {
                queue.add(load(start));
            }
        }
        while (!queue.isEmpty()) {
            Commit commit = queue.poll();
            result.add(commit);
            for (ObjectId parent : commit.parents()) {
                if (seen.add(parent)) {
                    queue.add(load(parent));
                }
            }
        }
        return result;
    }

    /** All commit ids reachable from {@code start}, including itself, in no particular order. O(V + E). */
    public Set<ObjectId> reachableFrom(ObjectId start) {
        return reachableFrom(List.of(start));
    }

    public Set<ObjectId> reachableFrom(List<ObjectId> starts) {
        Set<ObjectId> visited = new HashSet<>();
        Deque<ObjectId> stack = new ArrayDeque<>(starts);
        while (!stack.isEmpty()) {
            ObjectId id = stack.pop();
            if (!visited.add(id)) {
                continue;
            }
            Optional<Commit> commit = store.get(id).filter(o -> o instanceof Commit).map(o -> (Commit) o);
            commit.ifPresent(c -> stack.addAll(c.parents()));
        }
        return visited;
    }
}
