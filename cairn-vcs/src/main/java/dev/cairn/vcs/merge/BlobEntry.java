package dev.cairn.vcs.merge;

import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.ObjectId;

/** A file's mode and blob id, keyed by path elsewhere. The merge-side twin of {@code TreeEntry}/{@code Index.Entry}. */
record BlobEntry(FileMode mode, ObjectId id) {
}
