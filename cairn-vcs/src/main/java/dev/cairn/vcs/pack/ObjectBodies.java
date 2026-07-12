package dev.cairn.vcs.pack;

import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.GitObject;
import dev.cairn.vcs.object.ObjectKind;
import dev.cairn.vcs.object.Tag;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.Commit;

import java.util.Arrays;

/** Strips/rebuilds the {@code "<kind> <len>\0"} framing so pack entries can store just the body, as Git's pack format does. */
final class ObjectBodies {

    private ObjectBodies() {
    }

    static byte[] bodyOf(GitObject object) {
        byte[] raw = object.serialize();
        int nul = 0;
        while (raw[nul] != 0) {
            nul++;
        }
        return Arrays.copyOfRange(raw, nul + 1, raw.length);
    }

    static GitObject reconstruct(ObjectKind kind, byte[] body) {
        return switch (kind) {
            case BLOB -> new Blob(body);
            case TREE -> Tree.parse(body);
            case COMMIT -> Commit.parse(body);
            case TAG -> Tag.parse(body);
        };
    }
}
