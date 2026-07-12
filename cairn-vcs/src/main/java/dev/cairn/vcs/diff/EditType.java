package dev.cairn.vcs.diff;

/** The three edit kinds a diff decomposes into: unchanged runs, deletions, and insertions. */
public enum EditType {
    EQUAL,
    DELETE,
    INSERT
}
