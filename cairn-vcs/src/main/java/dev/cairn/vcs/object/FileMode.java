package dev.cairn.vcs.object;

/**
 * Unix-style mode bits recorded per tree entry, mirroring Git's small fixed set.
 *
 * <p>The directory mode is the literal string {@code "40000"}, not {@code "040000"}:
 * Git's tree encoding writes it without a leading zero, and since the encoding also
 * feeds the object hash, reproducing that quirk exactly is required for byte-for-byte
 * compatibility with real Git clients.
 */
public enum FileMode {
    REGULAR_FILE("100644"),
    EXECUTABLE_FILE("100755"),
    SYMLINK("120000"),
    DIRECTORY("40000");

    private final String octal;

    FileMode(String octal) {
        this.octal = octal;
    }

    public String octal() {
        return octal;
    }

    public static FileMode fromOctal(String octal) {
        for (FileMode mode : values()) {
            if (mode.octal.equals(octal)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown file mode: " + octal);
    }
}
