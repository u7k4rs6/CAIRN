package dev.cairn.vcs.diff;

import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.store.LooseObjectStore;
import dev.cairn.vcs.store.ObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeDiffTest {

    @Test
    void detectsAddedModifiedAndDeletedFiles(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir);

        ObjectId unchanged = store.put(new Blob("same\n".getBytes()));
        ObjectId oldContent = store.put(new Blob("old\n".getBytes()));
        ObjectId newContent = store.put(new Blob("new\n".getBytes()));
        ObjectId deletedContent = store.put(new Blob("gone\n".getBytes()));
        ObjectId addedContent = store.put(new Blob("fresh\n".getBytes()));

        ObjectId treeA = store.put(new Tree(List.of(
                new TreeEntry(FileMode.REGULAR_FILE, "unchanged.txt", unchanged),
                new TreeEntry(FileMode.REGULAR_FILE, "modified.txt", oldContent),
                new TreeEntry(FileMode.REGULAR_FILE, "deleted.txt", deletedContent)
        )));
        ObjectId treeB = store.put(new Tree(List.of(
                new TreeEntry(FileMode.REGULAR_FILE, "unchanged.txt", unchanged),
                new TreeEntry(FileMode.REGULAR_FILE, "modified.txt", newContent),
                new TreeEntry(FileMode.REGULAR_FILE, "added.txt", addedContent)
        )));

        List<FileDiff> diffs = TreeDiff.diff(store, treeA, treeB);

        assertThat(diffs).extracting(FileDiff::path).containsExactlyInAnyOrder("modified.txt", "deleted.txt", "added.txt");
        assertThat(diffs).filteredOn(d -> d.path().equals("modified.txt")).extracting(FileDiff::kind).containsExactly(ChangeKind.MODIFIED);
        assertThat(diffs).filteredOn(d -> d.path().equals("deleted.txt")).extracting(FileDiff::kind).containsExactly(ChangeKind.DELETED);
        assertThat(diffs).filteredOn(d -> d.path().equals("added.txt")).extracting(FileDiff::kind).containsExactly(ChangeKind.ADDED);
    }

    @Test
    void identicalTreesProduceNoDiffs(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir);
        ObjectId blob = store.put(new Blob("content\n".getBytes()));
        ObjectId tree = store.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "f.txt", blob))));
        assertThat(TreeDiff.diff(store, tree, tree)).isEmpty();
    }
}
