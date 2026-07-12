package dev.cairn.transfer;

import dev.cairn.vcs.dag.FileGenerationStore;
import dev.cairn.vcs.dag.GenerationStore;
import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.pack.PackWriter;
import dev.cairn.vcs.ref.FileRefStore;
import dev.cairn.vcs.ref.RefStore;
import dev.cairn.vcs.store.LooseObjectStore;
import dev.cairn.vcs.store.ObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivePackHandlerTest {

    private static final PersonIdent PERSON = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");

    @Test
    void pushCreatesANewBranchAndStoresItsObjects(@TempDir Path dir) {
        ObjectStore serverStore = new LooseObjectStore(dir.resolve("objects"));
        RefStore refs = new FileRefStore(dir.resolve("refs"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));

        // Build the commit/tree/blob a client would push, in its own throwaway store, then pack it.
        ObjectStore clientStore = new LooseObjectStore(dir.resolve("client-objects"));
        ObjectId blob = clientStore.put(new Blob("pushed content".getBytes()));
        ObjectId tree = clientStore.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "f.txt", blob))));
        ObjectId commit = clientStore.put(new Commit(tree, List.of(), PERSON, PERSON, "pushed"));
        byte[] pack = new PackWriter(clientStore).writePack(List.of(blob, tree, commit));

        ByteArrayOutputStream request = new ByteArrayOutputStream();
        request.writeBytes(PktLine.encode(RefAdvertisement.ZERO_ID + " " + commit.hex() + " refs/heads/main\0report-status\n"));
        request.writeBytes(PktLine.flush());
        request.writeBytes(pack);

        var outcome = ReceivePackHandler.handle(serverStore, refs, generations, new ByteArrayInputStream(request.toByteArray()), ReceivePackHandler.ALLOW_ALL);

        assertThat(outcome.unpackOk()).isTrue();
        assertThat(outcome.refResults()).containsExactly("ok refs/heads/main");
        assertThat(refs.resolve("refs/heads/main")).contains(commit);
        assertThat(serverStore.has(commit)).isTrue();
        assertThat(serverStore.has(tree)).isTrue();
        assertThat(serverStore.has(blob)).isTrue();
    }

    @Test
    void reportStatusResponseIsWellFormedPktLines() {
        var outcome = new ReceivePackHandler.Outcome(true, List.of("ok refs/heads/main"));
        byte[] response = ReceivePackHandler.buildReportStatus(outcome);
        var in = new ByteArrayInputStream(response);
        assertThat(PktLine.readLine(in)).isEqualTo("unpack ok");
        assertThat(PktLine.readLine(in)).isEqualTo("ok refs/heads/main");
        assertThat(PktLine.read(in)).isEqualTo(PktLine.FLUSH);
    }

    @Test
    void deletingARefSendsTheZeroIdAsNewValue(@TempDir Path dir) {
        RefStore refs = new FileRefStore(dir.resolve("refs"));
        ObjectStore store = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        ObjectId commit = store.put(new Commit(store.put(new Tree(List.of())), List.of(), PERSON, PERSON, "c"));
        refs.update("refs/heads/doomed", commit);

        ByteArrayOutputStream request = new ByteArrayOutputStream();
        request.writeBytes(PktLine.encode(commit.hex() + " " + RefAdvertisement.ZERO_ID + " refs/heads/doomed\0report-status\n"));
        request.writeBytes(PktLine.flush());

        var outcome = ReceivePackHandler.handle(store, refs, generations, new ByteArrayInputStream(request.toByteArray()), ReceivePackHandler.ALLOW_ALL);

        assertThat(outcome.refResults()).containsExactly("ok refs/heads/doomed");
        assertThat(refs.exists("refs/heads/doomed")).isFalse();
    }

    @Test
    void aDeniedUpdateRejectsTheWholePushAtomically(@TempDir Path dir) {
        // Security doc, section 4.3: "Only after all updates pass are refs written.
        // A violating push is rejected atomically." Two updates, one allowed and one
        // denied: neither should be written.
        ObjectStore store = new LooseObjectStore(dir.resolve("objects"));
        RefStore refs = new FileRefStore(dir.resolve("refs"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        ObjectId commit = store.put(new Commit(store.put(new Tree(List.of())), List.of(), PERSON, PERSON, "c"));

        ByteArrayOutputStream request = new ByteArrayOutputStream();
        request.writeBytes(PktLine.encode(RefAdvertisement.ZERO_ID + " " + commit.hex() + " refs/heads/allowed\0report-status\n"));
        request.writeBytes(PktLine.encode(RefAdvertisement.ZERO_ID + " " + commit.hex() + " refs/heads/blocked\n"));
        request.writeBytes(PktLine.flush());

        ReceivePackHandler.RefUpdateAuthorizer authorizer = command -> command.ref().equals("refs/heads/blocked")
                ? ReceivePackHandler.RefUpdateAuthorizer.Decision.deny("insufficient role")
                : ReceivePackHandler.RefUpdateAuthorizer.Decision.allow();

        var outcome = ReceivePackHandler.handle(store, refs, generations, new ByteArrayInputStream(request.toByteArray()), authorizer);

        assertThat(outcome.refResults()).containsExactlyInAnyOrder(
                "ng refs/heads/allowed transaction failed",
                "ng refs/heads/blocked insufficient role");
        assertThat(refs.exists("refs/heads/allowed")).isFalse();
        assertThat(refs.exists("refs/heads/blocked")).isFalse();
    }
}
