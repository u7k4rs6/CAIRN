package dev.cairn.vcs.object;

/** The four object kinds in the content-addressable store (architecture doc, section 4.1). */
public enum ObjectKind {
    BLOB("blob"),
    TREE("tree"),
    COMMIT("commit"),
    TAG("tag");

    private final String label;

    ObjectKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ObjectKind fromLabel(String label) {
        for (ObjectKind kind : values()) {
            if (kind.label.equals(label)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown object kind: " + label);
    }
}
