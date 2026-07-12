package dev.cairn.vcs.diff;

import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.store.ObjectStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Structural diff between two trees: which paths were added, deleted, or modified,
 * and for modified or added/deleted text files, the {@link MyersDiff} edit script.
 *
 * <p>Self-contained flattening rather than sharing {@code merge}'s tree flattener,
 * so {@code diff} does not depend on {@code merge} (dependencies point one way:
 * merge already depends on diff for {@link MyersDiff}, so the reverse would cycle).
 */
public final class TreeDiff {

    private TreeDiff() {
    }

    public static List<FileDiff> diff(ObjectStore store, ObjectId treeA, ObjectId treeB) {
        Map<String, ObjectId> a = flatten(store, treeA);
        Map<String, ObjectId> b = flatten(store, treeB);
        var paths = new TreeSet<String>();
        paths.addAll(a.keySet());
        paths.addAll(b.keySet());

        MyersDiff<String> myers = new MyersDiff<>();
        List<FileDiff> result = new ArrayList<>();
        for (String path : paths) {
            ObjectId idA = a.get(path);
            ObjectId idB = b.get(path);
            if (Objects.equals(idA, idB)) {
                continue;
            }
            List<String> linesA = idA != null ? Lines.of(((Blob) store.get(idA).orElseThrow()).content()) : List.of();
            List<String> linesB = idB != null ? Lines.of(((Blob) store.get(idB).orElseThrow()).content()) : List.of();
            ChangeKind kind = idA == null ? ChangeKind.ADDED : idB == null ? ChangeKind.DELETED : ChangeKind.MODIFIED;
            result.add(new FileDiff(path, kind, myers.diff(linesA, linesB)));
        }
        return result;
    }

    private static Map<String, ObjectId> flatten(ObjectStore store, ObjectId treeId) {
        Map<String, ObjectId> result = new TreeMap<>();
        walk(store, treeId, "", result);
        return result;
    }

    private static void walk(ObjectStore store, ObjectId treeId, String prefix, Map<String, ObjectId> out) {
        Tree tree = (Tree) store.get(treeId).orElseThrow(() -> new IllegalStateException("missing tree " + treeId));
        for (TreeEntry entry : tree.entries()) {
            String path = prefix.isEmpty() ? entry.name() : prefix + "/" + entry.name();
            if (entry.isDirectory()) {
                walk(store, entry.id(), path, out);
            } else {
                out.put(path, entry.id());
            }
        }
    }
}
