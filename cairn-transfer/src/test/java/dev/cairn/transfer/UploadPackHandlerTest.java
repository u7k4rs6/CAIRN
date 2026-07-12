package dev.cairn.transfer;

import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.pack.PackReader;
import dev.cairn.vcs.store.LooseObjectStore;
import dev.cairn.vcs.store.ObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-XFER-1 directly: the server packs only the objects the client's haves don't already cover. */
class UploadPackHandlerTest {

    private static final PersonIdent PERSON = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");

    private ObjectId commitWithFile(ObjectStore store, String path, String content, List<ObjectId> parents) {
        ObjectId blob = store.put(new Blob(content.getBytes()));
        ObjectId tree = store.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, path, blob))));
        return store.put(new Commit(tree, parents, PERSON, PERSON, "commit " + path));
    }

    @Test
    void freshCloneReceivesEveryReachableObject(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir);
        ObjectId c1 = commitWithFile(store, "a.txt", "one", List.of());
        ObjectId c2 = commitWithFile(store, "b.txt", "two", List.of(c1));

        UploadPackHandler.Request request = new UploadPackHandler.Request(List.of(c2), List.of());
        byte[] response = UploadPackHandler.buildResponse(store, request);

        assertThat(new String(response, 0, 4)).isEqualTo("0008"); // "NAK\n" pkt-line length: 4 (header) + 4 ("NAK\n")
        byte[] pack = extractPackBytes(response);
        var objects = PackReader.read(pack);
        // c1, c2, two trees, two blobs = 6 objects for a fresh clone.
        assertThat(objects).hasSize(6);
        assertThat(objects).containsKey(c1);
        assertThat(objects).containsKey(c2);
    }

    @Test
    void fetchAfterNewCommitsSendsOnlyTheNewObjects(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir);
        ObjectId c1 = commitWithFile(store, "a.txt", "one", List.of());
        ObjectId c2 = commitWithFile(store, "b.txt", "two", List.of(c1));
        ObjectId c3 = commitWithFile(store, "c.txt", "three", List.of(c2));

        // Client already has everything up to c2; wants c3.
        UploadPackHandler.Request request = new UploadPackHandler.Request(List.of(c3), List.of(c2));
        byte[] response = UploadPackHandler.buildResponse(store, request);
        byte[] pack = extractPackBytes(response);
        var objects = PackReader.read(pack);

        // Only c3's own commit, tree, and blob are new: 3 objects, not the full 9 reachable from c3.
        assertThat(objects).hasSize(3);
        assertThat(objects).containsKey(c3);
        assertThat(objects).doesNotContainKey(c1);
        assertThat(objects).doesNotContainKey(c2);
    }

    @Test
    void requestParsingExtractsWantsAndHaves() {
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        body.writeBytes(PktLine.encode("want " + "a".repeat(40) + " capabilities^{}\n"));
        body.writeBytes(PktLine.flush());
        body.writeBytes(PktLine.encode("have " + "b".repeat(40) + "\n"));
        body.writeBytes(PktLine.encode("done\n"));

        var in = new java.io.ByteArrayInputStream(body.toByteArray());
        UploadPackHandler.Request request = UploadPackHandler.parseRequest(in);
        assertThat(request.wants()).containsExactly(ObjectId.fromHex("a".repeat(40)));
        assertThat(request.haves()).containsExactly(ObjectId.fromHex("b".repeat(40)));
    }

    private byte[] extractPackBytes(byte[] response) {
        // Skip the "NAK\n" pkt-line (4-byte length header + payload) to get raw pack bytes.
        int nakLength = Integer.parseInt(new String(response, 0, 4), 16);
        return java.util.Arrays.copyOfRange(response, nakLength, response.length);
    }
}
