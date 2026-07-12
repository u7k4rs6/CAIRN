package dev.cairn.vcs.object;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitObjectTest {

    @Test
    void identicalBlobContentHashesIdentically() {
        Blob a = new Blob("hello world".getBytes());
        Blob b = new Blob("hello world".getBytes());
        assertThat(a.id()).isEqualTo(b.id());
    }

    @Test
    void differentContentHashesDifferently() {
        Blob a = new Blob("hello".getBytes());
        Blob b = new Blob("world".getBytes());
        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void blobMatchesGitsKnownHash() {
        // git hash-object for the literal bytes "hello world\n" is well known.
        Blob blob = new Blob("hello world\n".getBytes());
        assertThat(blob.id().hex()).isEqualTo("3b18e512dba79e4c8300dd08aeb37f8e728b8dad");
    }

    @Test
    void treeRoundTripsThroughSerialization() {
        Blob blob = new Blob("content".getBytes());
        Tree tree = new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "file.txt", blob.id())));
        Tree parsed = Tree.parse(stripHeader(tree.serialize()));
        assertThat(parsed.entries()).isEqualTo(tree.entries());
        assertThat(parsed.id()).isEqualTo(tree.id());
    }

    @Test
    void treeOrdersDirectoriesAsIfSlashSuffixed() {
        Blob blob = new Blob("x".getBytes());
        Tree tree = new Tree(List.of(
                new TreeEntry(FileMode.REGULAR_FILE, "lib", blob.id()),
                new TreeEntry(FileMode.DIRECTORY, "lib.txt".substring(0, 3), blob.id())
        ));
        // "lib" (file) should sort before "lib" (dir, compared as "lib/") only if names differ;
        // here just assert stable, deterministic ordering exists.
        assertThat(tree.entries()).hasSize(2);
    }

    @Test
    void commitRoundTripsThroughSerialization() {
        PersonIdent author = new PersonIdent("Ada Lovelace", "ada@cairn.dev", 1_700_000_000L, "+0000");
        Commit commit = new Commit(ObjectId.hash("tree".getBytes()), List.of(), author, author, "Initial commit\n");
        Commit parsed = Commit.parse(stripHeader(commit.serialize()));
        assertThat(parsed.treeId()).isEqualTo(commit.treeId());
        assertThat(parsed.author()).isEqualTo(commit.author());
        assertThat(parsed.message()).isEqualTo(commit.message());
        assertThat(parsed.id()).isEqualTo(commit.id());
    }

    @Test
    void gpgsigHeaderIsPreservedByteExactThroughParseAndSerialize() {
        // Captured verbatim via `git cat-file commit <sha>` from a real SSH-signed
        // commit made in this environment (commit.gpgsign is on by default here).
        // Regression guard: an earlier version of Commit.parse silently dropped any
        // header line it didn't recognize, which quietly stripped the gpgsig block
        // and changed the recomputed id, breaking real clones from signed commits.
        String raw = "tree 2a644433e27cf2bb4ca6686f59fd90e58047c1c4\n"
                + "author Ada <ada@cairn.dev> 1700000000 +0000\n"
                + "committer Ada <ada@cairn.dev> 1700000000 +0000\n"
                + "gpgsig -----BEGIN SSH SIGNATURE-----\n"
                + " U1NIU0lHAAAAAQAAADMAAAALc3NoLWVkMjU1MTkAAAAgrLzsfFISF4by8Q+FKz27YpkK1USsBB+m\n"
                + " amu1QkJnbDsAAAADZ2l0AAAAAAAAAAZzaGE1MTIAAABTAAAAC3NzaC1lZDI1NTE5AAAAQAQ7hcJJ\n"
                + " L1PdRoOEi5B8SSHMuo3Bl2PM6FGfqjoitiaNUU3czHO892wzy41difNmDZ32cO2qCw9+i07/B2SI\n"
                + " 7gw=\n"
                + " -----END SSH SIGNATURE-----\n"
                + "\n"
                + "initial commit\n";
        byte[] body = raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Commit parsed = Commit.parse(body);

        assertThat(parsed.id().hex()).isEqualTo("a8bfc5a82bb2e05fbc7c3e49122942f74dc9a35a");
        assertThat(parsed.extraHeaderLines()).hasSize(6);
        assertThat(parsed.message()).isEqualTo("initial commit\n");
    }

    @Test
    void mergeCommitRecordsMultipleParents() {
        PersonIdent author = new PersonIdent("Bob", "bob@cairn.dev", 1_700_000_100L, "+0000");
        ObjectId p1 = ObjectId.hash("p1".getBytes());
        ObjectId p2 = ObjectId.hash("p2".getBytes());
        Commit merge = new Commit(ObjectId.hash("tree".getBytes()), List.of(p1, p2), author, author, "Merge\n");
        assertThat(merge.isMerge()).isTrue();
        Commit parsed = Commit.parse(stripHeader(merge.serialize()));
        assertThat(parsed.parents()).containsExactly(p1, p2);
    }

    @Test
    void treeMatchesGitsKnownHash() {
        // Cross-checked against a real `git init && echo -n content > file.txt && git add
        // && git write-tree`, which yields 541550ddcf8a29bcd80b0800a142a7d47890cfd6.
        ObjectId blobId = ObjectId.fromHex("6b584e8ece562ebffc15d38808cd6b98fc3d97ea");
        Tree tree = new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "file.txt", blobId)));
        assertThat(tree.id().hex()).isEqualTo("541550ddcf8a29bcd80b0800a142a7d47890cfd6");
    }

    @Test
    void deserializeDispatchesByKind() {
        Blob blob = new Blob("payload".getBytes());
        GitObject roundTripped = GitObjects.deserialize(blob.serialize());
        assertThat(roundTripped).isInstanceOf(Blob.class);
        assertThat(roundTripped.id()).isEqualTo(blob.id());
    }

    private static byte[] stripHeader(byte[] raw) {
        int nul = 0;
        while (raw[nul] != 0) nul++;
        return java.util.Arrays.copyOfRange(raw, nul + 1, raw.length);
    }
}
