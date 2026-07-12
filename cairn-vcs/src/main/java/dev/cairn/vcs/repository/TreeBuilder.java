package dev.cairn.vcs.repository;

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
 * Builds the nested {@link Tree} objects for a commit from a flat path-to-blob map,
 * the way a real working tree is a nested structure but the index stores it flat.
 *
 * <p>Cost is O(number of staged paths x average path depth): every path contributes
 * one leaf entry, and every distinct directory along the way is visited once to
 * collect its entries before being serialized bottom-up.
 */
public final class TreeBuilder {

    private final ObjectStore store;

    public TreeBuilder(ObjectStore store) {
        this.store = store;
    }

    public ObjectId build(Map<String, Index.Entry> flatEntries) {
        Node root = new Node();
        for (Map.Entry<String, Index.Entry> e : flatEntries.entrySet()) {
            String[] segments = e.getKey().split("/");
            Node current = root;
            for (int i = 0; i < segments.length - 1; i++) {
                current = current.children.computeIfAbsent(segments[i], k -> new Node());
            }
            current.blobs.put(segments[segments.length - 1], e.getValue());
        }
        return writeTree(root);
    }

    private ObjectId writeTree(Node node) {
        List<TreeEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Index.Entry> e : node.blobs.entrySet()) {
            entries.add(new TreeEntry(e.getValue().mode(), e.getKey(), e.getValue().blobId()));
        }
        for (Map.Entry<String, Node> e : node.children.entrySet()) {
            ObjectId subtreeId = writeTree(e.getValue());
            entries.add(new TreeEntry(FileMode.DIRECTORY, e.getKey(), subtreeId));
        }
        Tree tree = new Tree(entries);
        return store.put(tree);
    }

    private static final class Node {
        final Map<String, Index.Entry> blobs = new TreeMap<>();
        final Map<String, Node> children = new TreeMap<>();
    }
}
