package dev.cairn.vcs.blame;

import dev.cairn.vcs.diff.Edit;
import dev.cairn.vcs.diff.EditType;
import dev.cairn.vcs.diff.Lines;
import dev.cairn.vcs.diff.MyersDiff;
import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.GitObject;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.store.ObjectStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * FR-BROWSE-1: attributes each line of a file, at a given commit, to the commit
 * that last changed it, by walking history and carrying line provenance backward
 * through a diff at each step (the real algorithmic content the PRD calls for,
 * not a stub that just reports the file's latest commit for every line).
 *
 * <p><b>Algorithm.</b> Start with every line of the target commit's version of the
 * file marked unattributed. At each step, diff the current commit's version of the
 * file against its first parent's version (reusing {@link MyersDiff}, the same
 * engine {@code diff} and three-way merge already use). A line that is
 * {@code EQUAL} in that diff survived unchanged from the parent, so it stays
 * unattributed and carries forward to be checked one commit further back; a line
 * that only exists on the child side ({@code INSERT}) was introduced at this
 * commit, so it is attributed here and removed from further consideration. Once
 * every line is attributed, or a commit with no parent is reached (whatever
 * remains is attributed to that root commit), the walk stops.
 *
 * <p><b>Complexity and tradeoff.</b> O(commits walked x diff cost per commit), and
 * the walk follows only the first parent at a merge, matching this project's
 * existing rename-detection limitation (architecture doc, section on named merge
 * limitations): a line whose true origin is on a merge's second parent is
 * attributed to the merge commit itself rather than traced further, and a file
 * move or rename is treated as an unrelated delete-and-add rather than followed
 * across the rename, exactly like {@code TreeMerger}'s own stated scope boundary.
 * Real Git's blame adds move/copy detection and a generation-number-guided search
 * to prune commits that cannot possibly be the origin; neither is attempted here.
 * The walk still stops as soon as every line is attributed rather than always
 * consuming the whole history, so a file whose lines are all recently touched is
 * cheap regardless of how long the repository's history is.
 */
public final class Blame {

    private Blame() {
    }

    public record LineBlame(int lineNumber, ObjectId commitId, String line) {
    }

    public static List<LineBlame> blame(ObjectStore store, ObjectId startCommitId, String path) {
        Commit startCommit = (Commit) store.get(startCommitId).orElseThrow();
        List<String> targetLines = fileLinesAt(store, startCommit, path);

        ObjectId[] attribution = new ObjectId[targetLines.size()];
        List<String> currentLines = new ArrayList<>(targetLines);
        List<Integer> targetIndex = new ArrayList<>(targetLines.size());
        for (int i = 0; i < targetLines.size(); i++) {
            targetIndex.add(i);
        }

        Commit commit = startCommit;
        while (!currentLines.isEmpty()) {
            if (commit.parents().isEmpty()) {
                for (int i = 0; i < currentLines.size(); i++) {
                    attribution[targetIndex.get(i)] = commit.id();
                }
                break;
            }
            ObjectId parentId = commit.parents().get(0);
            Commit parent = (Commit) store.get(parentId).orElseThrow();
            List<String> parentLines = fileLinesAt(store, parent, path);

            List<Edit> edits = new MyersDiff<String>().diff(parentLines, currentLines);
            List<String> nextLines = new ArrayList<>();
            List<Integer> nextTargetIndex = new ArrayList<>();
            for (Edit edit : edits) {
                if (edit.type() == EditType.EQUAL) {
                    for (int i = edit.revStart(); i < edit.revEnd(); i++) {
                        nextLines.add(currentLines.get(i));
                        nextTargetIndex.add(targetIndex.get(i));
                    }
                } else if (edit.type() == EditType.INSERT) {
                    for (int i = edit.revStart(); i < edit.revEnd(); i++) {
                        attribution[targetIndex.get(i)] = commit.id();
                    }
                }
                // DELETE ranges exist only in the parent's version and never appear
                // in currentLines, so there is nothing to carry or attribute here.
            }

            currentLines = nextLines;
            targetIndex = nextTargetIndex;
            commit = parent;
        }

        List<LineBlame> result = new ArrayList<>(targetLines.size());
        for (int i = 0; i < targetLines.size(); i++) {
            result.add(new LineBlame(i + 1, attribution[i], targetLines.get(i)));
        }
        return result;
    }

    /** Empty if {@code path} does not exist in {@code commit}'s tree (the file was added later, from this commit's point of view). */
    private static List<String> fileLinesAt(ObjectStore store, Commit commit, String path) {
        Optional<ObjectId> blobId = navigateToBlob(store, commit.treeId(), path);
        if (blobId.isEmpty()) {
            return List.of();
        }
        Blob blob = (Blob) store.get(blobId.get()).orElseThrow();
        return Lines.of(blob.content());
    }

    private static Optional<ObjectId> navigateToBlob(ObjectStore store, ObjectId treeId, String path) {
        String[] segments = path.split("/");
        ObjectId currentId = treeId;
        for (int i = 0; i < segments.length; i++) {
            GitObject obj = store.get(currentId).orElse(null);
            if (!(obj instanceof Tree tree)) {
                return Optional.empty();
            }
            Optional<TreeEntry> entry = tree.entry(segments[i]);
            if (entry.isEmpty()) {
                return Optional.empty();
            }
            if (i == segments.length - 1) {
                return entry.get().isDirectory() ? Optional.empty() : Optional.of(entry.get().id());
            }
            currentId = entry.get().id();
        }
        return Optional.empty();
    }
}
