package dev.cairn.vcs.diff;

import java.util.List;

/** One changed path between two trees, with its line-level edit script. */
public record FileDiff(String path, ChangeKind kind, List<Edit> edits) {
}
