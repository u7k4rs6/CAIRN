package dev.cairn.vcs.merge;

import java.util.List;

/** One unresolved hunk in a file: the base, ours, and theirs text for the same region. */
public record Conflict(String path, List<String> base, List<String> ours, List<String> theirs) {
}
