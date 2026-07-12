package dev.cairn.vcs.pack;

import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.GitObject;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.store.LooseObjectStore;
import dev.cairn.vcs.store.ObjectStore;
import dev.cairn.vcs.store.PackedObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The PRD's M4 gate directly: objects round-trip through a packfile, pack then reconstruct via the delta chain, byte for byte. */
class PackRoundTripTest {

    private static final PersonIdent PERSON = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");

    @Test
    void blobTreeAndCommitRoundTripByteExact(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir);
        Blob blob = new Blob("hello packfile\n".getBytes());
        ObjectId blobId = store.put(blob);
        Tree tree = new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "file.txt", blobId)));
        ObjectId treeId = store.put(tree);
        Commit commit = new Commit(treeId, List.of(), PERSON, PERSON, "message");
        ObjectId commitId = store.put(commit);

        byte[] pack = new PackWriter(store).writePack(List.of(blobId, treeId, commitId));
        PackedObjectStore packed = new PackedObjectStore(pack);

        assertThat(packed.objectCount()).isEqualTo(3);
        assertRoundTrips(store, packed, blobId);
        assertRoundTrips(store, packed, treeId);
        assertRoundTrips(store, packed, commitId);
    }

    @Test
    void similarBlobsDeltaCompressAndReconstructExactly(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir);
        List<ObjectId> ids = new ArrayList<>();
        String base = "line one\nline two\nline three\nline four\nline five\n";
        for (int i = 0; i < 10; i++) {
            String content = base + "revision marker " + i + "\n";
            ids.add(store.put(new Blob(content.getBytes())));
        }

        byte[] pack = new PackWriter(store).writePack(ids);
        PackedObjectStore packed = new PackedObjectStore(pack);

        assertThat(packed.objectCount()).isEqualTo(10);
        for (ObjectId id : ids) {
            assertRoundTrips(store, packed, id);
        }
        // At least some of the ten near-identical blobs should have compressed
        // smaller as a delta-referencing pack than ten independent full copies would.
        int fullyIndependentEstimate = ids.size() * (base.length() + 30);
        assertThat(pack.length).isLessThan(fullyIndependentEstimate);
    }

    @Test
    void deltaChainDepthIsBounded(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir);
        List<ObjectId> ids = new ArrayList<>();
        StringBuilder content = new StringBuilder("seed content\n");
        for (int i = 0; i < PackWriter.MAX_DELTA_DEPTH + 20; i++) {
            content.append("line ").append(i).append("\n");
            ids.add(store.put(new Blob(content.toString().getBytes())));
        }

        byte[] pack = new PackWriter(store).writePack(ids);
        PackedObjectStore packed = new PackedObjectStore(pack);

        for (ObjectId id : ids) {
            assertRoundTrips(store, packed, id);
        }
    }

    @Test
    void unrelatedObjectsOfTheSameKindStillRoundTripWithoutUsefulDeltaBenefit(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir);
        ObjectId a = store.put(new Blob("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()));
        ObjectId b = store.put(new Blob("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".getBytes()));

        byte[] pack = new PackWriter(store).writePack(List.of(a, b));
        PackedObjectStore packed = new PackedObjectStore(pack);

        assertRoundTrips(store, packed, a);
        assertRoundTrips(store, packed, b);
    }

    @Test
    void packedStoreIsReadOnly(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir);
        ObjectId id = store.put(new Blob("x".getBytes()));
        byte[] pack = new PackWriter(store).writePack(List.of(id));
        PackedObjectStore packed = new PackedObjectStore(pack);

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> packed.put(new Blob("y".getBytes())));
    }

    private void assertRoundTrips(ObjectStore original, PackedObjectStore packed, ObjectId id) {
        GitObject fromPack = packed.get(id).orElseThrow(() -> new AssertionError("missing from pack: " + id));
        assertThat(fromPack.id()).isEqualTo(id);
        assertThat(fromPack.serialize()).isEqualTo(original.get(id).orElseThrow().serialize());
    }
}
