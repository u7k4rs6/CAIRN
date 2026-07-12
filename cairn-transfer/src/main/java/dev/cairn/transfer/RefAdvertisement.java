package dev.cairn.transfer;

import dev.cairn.vcs.object.ObjectId;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the {@code info/refs} response for either service: a pkt-line "# service=..."
 * line, a flush, then one line per ref (the first carrying capabilities after a NUL),
 * then a final flush (architecture doc, section 7.2).
 *
 * <p><b>HEAD matters for clone.</b> A real client needs to know which branch to check
 * out after cloning, which it learns from a {@code HEAD} entry in the advertisement
 * (resolved here to the same id as the default branch) and a {@code symref} capability
 * naming that branch. Without either, a clone still succeeds at the object-transfer
 * level but leaves the working directory empty, since the client has nothing to tell
 * it "check out this branch" the way it normally would.
 */
public final class RefAdvertisement {

    public static final String ZERO_ID = "0".repeat(40);

    private RefAdvertisement() {
    }

    public static byte[] build(String service, Map<String, ObjectId> refs, String capabilities) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PktLine.encode("# service=" + service + "\n"));
        out.writeBytes(PktLine.flush());

        Map<String, ObjectId> sorted = new TreeMap<>(refs);
        if (sorted.isEmpty()) {
            out.writeBytes(PktLine.encode(ZERO_ID + " capabilities^{}\0" + capabilities + "\n"));
        } else {
            String defaultBranch = defaultBranch(sorted.keySet());
            String effectiveCapabilities = capabilities + " symref=HEAD:" + defaultBranch;

            out.writeBytes(PktLine.encode(sorted.get(defaultBranch).hex() + " HEAD\0" + effectiveCapabilities + "\n"));
            for (Map.Entry<String, ObjectId> entry : sorted.entrySet()) {
                out.writeBytes(PktLine.encode(entry.getValue().hex() + " " + entry.getKey() + "\n"));
            }
        }
        out.writeBytes(PktLine.flush());
        return out.toByteArray();
    }

    private static String defaultBranch(java.util.Set<String> refNames) {
        if (refNames.contains("refs/heads/main")) {
            return "refs/heads/main";
        }
        if (refNames.contains("refs/heads/master")) {
            return "refs/heads/master";
        }
        return refNames.iterator().next();
    }
}
