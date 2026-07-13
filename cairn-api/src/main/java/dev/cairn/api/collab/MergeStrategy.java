package dev.cairn.api.collab;

/** How a pull request's changes land on the target branch (architecture doc, section 6, Strategy row). */
public enum MergeStrategy {
    MERGE_COMMIT,
    SQUASH,
    REBASE
}
