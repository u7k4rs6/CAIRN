package dev.cairn.vcs.object;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A directory: an ordered set of entries, each naming a blob or a subtree.
 *
 * <p><b>Wire format.</b> Each entry is {@code "<mode> <name>\0<20-byte raw id>"},
 * concatenated with no separators between entries. This mixed text-and-binary layout
 * looks unusual next to {@link Commit}'s pure text format, but it is Git's actual
 * on-disk and on-wire tree encoding, and matching it is what lets a real {@code git}
 * client parse a tree Cairn produces.
 */
public final class Tree extends AbstractGitObject {

    private final List<TreeEntry> entries;

    public Tree(List<TreeEntry> entries) {
        List<TreeEntry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted);
        this.entries = List.copyOf(sorted);
    }

    public List<TreeEntry> entries() {
        return entries;
    }

    public java.util.Optional<TreeEntry> entry(String name) {
        return entries.stream().filter(e -> e.name().equals(name)).findFirst();
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.TREE;
    }

    @Override
    protected byte[] serializeBody() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (TreeEntry entry : entries) {
            out.writeBytes((entry.mode().octal() + " " + entry.name()).getBytes(StandardCharsets.UTF_8));
            out.write(0);
            out.writeBytes(entry.id().bytes());
        }
        return out.toByteArray();
    }

    public static Tree parse(byte[] body) {
        List<TreeEntry> entries = new ArrayList<>();
        int pos = 0;
        while (pos < body.length) {
            int space = indexOf(body, (byte) ' ', pos);
            String mode = new String(body, pos, space - pos, StandardCharsets.UTF_8);
            int nul = indexOf(body, (byte) 0, space + 1);
            String name = new String(body, space + 1, nul - space - 1, StandardCharsets.UTF_8);
            byte[] idBytes = java.util.Arrays.copyOfRange(body, nul + 1, nul + 1 + 20);
            entries.add(new TreeEntry(FileMode.fromOctal(mode), name, ObjectId.of(idBytes)));
            pos = nul + 1 + 20;
        }
        return new Tree(entries);
    }

    private static int indexOf(byte[] arr, byte target, int from) {
        for (int i = from; i < arr.length; i++) {
            if (arr[i] == target) {
                return i;
            }
        }
        throw new IllegalArgumentException("malformed tree body: expected byte " + target + " after " + from);
    }
}
