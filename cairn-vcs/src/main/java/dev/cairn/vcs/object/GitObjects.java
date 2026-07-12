package dev.cairn.vcs.object;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Factory pattern: turns raw framed bytes ({@code "<kind> <len>\0<body>"}) back into
 * the concrete {@link GitObject} they represent. Centralizing the kind-to-class
 * decision here is what keeps every caller from needing its own if/else on the kind
 * label (architecture doc, section 6, Factory row).
 */
public final class GitObjects {

    private GitObjects() {
    }

    public static GitObject deserialize(byte[] raw) {
        int nul = indexOf(raw, (byte) 0);
        String header = new String(raw, 0, nul, StandardCharsets.UTF_8);
        int space = header.indexOf(' ');
        ObjectKind kind = ObjectKind.fromLabel(header.substring(0, space));
        int length = Integer.parseInt(header.substring(space + 1));
        byte[] body = Arrays.copyOfRange(raw, nul + 1, nul + 1 + length);
        return switch (kind) {
            case BLOB -> new Blob(body);
            case TREE -> Tree.parse(body);
            case COMMIT -> Commit.parse(body);
            case TAG -> Tag.parse(body);
        };
    }

    private static int indexOf(byte[] arr, byte target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) {
                return i;
            }
        }
        throw new IllegalArgumentException("malformed object: no header terminator found");
    }
}
