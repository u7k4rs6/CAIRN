package dev.cairn.vcs.merge;

import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.store.ObjectStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts between a nested {@link Tree} and a flat path-to-blob map, purely within
 * the merge package: merge treats a tree as a set of files by path, not as a
 * recursive structure, which is what lets non-overlapping changes in different
 * subtrees merge without either side needing to touch the other's directories.
 *
 * <p>Deliberately independent of {@code repository.TreeBuilder}/{@code Index}, even
 * though the shapes are similar, so {@code merge} does not depend on {@code repository}:
 * porcelain (which uses both diff/merge and the index) sits above both, and a
 * dependency the other way would create a cycle.
 */
final class TreeFlattener {

    private TreeFlattener() {
    }

    static Map<String, BlobEntry> flatten(ObjectStore store, ObjectId treeId) {
        Map<String, BlobEntry> result = new TreeMap<>();
        walk(store, treeId, "", result);
        return result;
    }

    private static void walk(ObjectStore store, ObjectId treeId, String prefix, Map<String, BlobEntry> out) {
        Tree tree = (Tree) store.get(treeId).orElseThrow(() -> new IllegalStateException("missing tree " + treeId));
        for (TreeEntry entry : tree.entries()) {
            String path = prefix.isEmpty() ? entry.name() : prefix + "/" + entry.name();
            if (entry.isDirectory()) {
                walk(store, entry.id(), path, out);
            } else {
                out.put(path, new BlobEntry(entry.mode(), entry.id()));
            }
        }
    }

    static ObjectId build(ObjectStore store, Map<String, BlobEntry> flat) {
        Node root = new Node();
        for (Map.Entry<String, BlobEntry> e : flat.entrySet()) {
            String[] segments = e.getKey().split("/");
            Node current = root;
            for (int i = 0; i < segments.length - 1; i++) {
                current = current.children.computeIfAbsent(segments[i], k -> new Node());
            }
            current.blobs.put(segments[segments.length - 1], e.getValue());
        }
        return write(store, root);
    }

    private static ObjectId write(ObjectStore store, Node node) {
        List<TreeEntry> entries = new ArrayList<>();
        node.blobs.forEach((name, blob) -> entries.add(new TreeEntry(blob.mode(), name, blob.id())));
        node.children.forEach((name, child) -> entries.add(new TreeEntry(FileMode.DIRECTORY, name, write(store, child))));
        return store.put(new Tree(entries));
    }

    private static final class Node {
        final Map<String, BlobEntry> blobs = new TreeMap<>();
        final Map<String, Node> children = new TreeMap<>();
    }
}
