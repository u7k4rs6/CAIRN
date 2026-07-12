package dev.cairn.vcs.repository;

import dev.cairn.vcs.dag.FileGenerationStore;
import dev.cairn.vcs.dag.GenerationNumbers;
import dev.cairn.vcs.dag.GenerationStore;
import dev.cairn.vcs.dag.RevWalk;
import dev.cairn.vcs.diff.FileDiff;
import dev.cairn.vcs.diff.TreeDiff;
import dev.cairn.vcs.merge.MergeEngine;
import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.ref.FileRefStore;
import dev.cairn.vcs.ref.Head;
import dev.cairn.vcs.ref.RefStore;
import dev.cairn.vcs.store.LooseObjectStore;
import dev.cairn.vcs.store.ObjectStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The porcelain facade: {@code init}, {@code add}, {@code commit}, {@code log}, and
 * (from M2 onward) {@code branch}, {@code checkout}, {@code diff}, {@code merge}.
 * Everything below this class is plumbing; this is the one type most callers touch.
 */
public final class Repository {

    public static final String CAIRN_DIR = ".cairn";
    public static final String DEFAULT_BRANCH = "refs/heads/main";

    private final Path workingDir;
    private final Path cairnDir;
    private final ObjectStore objectStore;
    private final RefStore refStore;
    private final Head head;
    private final Index index;
    private final GenerationStore generations;

    private Repository(Path workingDir) {
        this.workingDir = workingDir;
        this.cairnDir = workingDir.resolve(CAIRN_DIR);
        this.objectStore = new LooseObjectStore(cairnDir.resolve("objects"));
        this.refStore = new FileRefStore(cairnDir.resolve("refs"));
        this.head = new Head(cairnDir.resolve("HEAD"), refStore);
        this.index = new Index(cairnDir.resolve("index"));
        this.generations = new FileGenerationStore(cairnDir.resolve("generations"));
    }

    public static Repository init(Path workingDir) {
        Repository repo = new Repository(workingDir);
        if (!repo.head.currentBranch().isPresent() && repo.head.resolve().isEmpty()) {
            repo.head.pointToBranch(DEFAULT_BRANCH);
        }
        return repo;
    }

    public static Repository open(Path workingDir) {
        if (!Files.isDirectory(workingDir.resolve(CAIRN_DIR))) {
            throw new IllegalStateException("not a cairn repository: " + workingDir);
        }
        return new Repository(workingDir);
    }

    public static boolean isRepository(Path workingDir) {
        return Files.isDirectory(workingDir.resolve(CAIRN_DIR));
    }

    public Path workingDir() {
        return workingDir;
    }

    public ObjectStore objectStore() {
        return objectStore;
    }

    public RefStore refStore() {
        return refStore;
    }

    public Head head() {
        return head;
    }

    public Index index() {
        return index;
    }

    /** Stages a working-tree-relative file: hashes its content into a blob and records it in the index. */
    public ObjectId add(String relativePath) {
        Path file = workingDir.resolve(relativePath);
        try {
            byte[] content = Files.readAllBytes(file);
            var blob = new dev.cairn.vcs.object.Blob(content);
            ObjectId blobId = objectStore.put(blob);
            FileMode mode = Files.isExecutable(file) ? FileMode.EXECUTABLE_FILE : FileMode.REGULAR_FILE;
            index.stage(normalize(relativePath), mode, blobId);
            return blobId;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String normalize(String path) {
        return path.replace('\\', '/');
    }

    /**
     * Builds a tree from the current index, creates a commit whose parent is the
     * current HEAD (absent for the first commit), and advances HEAD to it.
     */
    public ObjectId commit(String message, PersonIdent author, PersonIdent committer) {
        ObjectId treeId = new TreeBuilder(objectStore).build(index.entries());
        Optional<ObjectId> parent = head.resolve();
        List<ObjectId> parents = parent.map(List::of).orElse(List.of());
        Commit commit = new Commit(treeId, parents, author, committer, message);
        ObjectId commitId = objectStore.put(commit);
        GenerationNumbers.computeAndStore(objectStore, generations, commitId);
        if (head.isDetached() && head.resolve().isEmpty()) {
            head.pointToBranch(DEFAULT_BRANCH);
        }
        head.advance(commitId);
        return commitId;
    }

    /** History reachable from HEAD, newest first. */
    public List<Commit> log() {
        return head.resolve().map(id -> new RevWalk(objectStore).history(List.of(id))).orElse(List.of());
    }

    private static final String BRANCH_PREFIX = "refs/heads/";

    /** Creates a branch at {@code startPoint} (or HEAD's commit if absent), without switching to it. */
    public void createBranch(String name, ObjectId startPoint) {
        refStore.update(BRANCH_PREFIX + name, startPoint);
    }

    public void createBranch(String name) {
        ObjectId start = head.resolve().orElseThrow(() -> new IllegalStateException("HEAD has no commit yet"));
        createBranch(name, start);
    }

    public List<String> listBranches() {
        return refStore.list().keySet().stream()
                .filter(r -> r.startsWith(BRANCH_PREFIX))
                .map(r -> r.substring(BRANCH_PREFIX.length()))
                .sorted()
                .toList();
    }

    /** Resolves a branch name or a raw commit id to a commit id. */
    private ObjectId resolveCommitish(String ref) {
        Optional<ObjectId> branch = refStore.resolve(BRANCH_PREFIX + ref);
        if (branch.isPresent()) {
            return branch.get();
        }
        if (refStore.exists(ref)) {
            return refStore.resolve(ref).orElseThrow();
        }
        return ObjectId.fromHex(ref);
    }

    /**
     * Switches HEAD to {@code ref} (a branch name or a raw commit id) and materializes
     * its tree into the working directory, replacing the index wholesale. Files
     * tracked by the previous commit but absent from the new one are removed; this
     * does not touch files the new tree also doesn't track and the old one didn't
     * either (untracked files are left alone, as in a real checkout).
     */
    public void checkout(String ref) {
        ObjectId targetCommitId = resolveCommitish(ref);
        Commit target = (Commit) objectStore.get(targetCommitId)
                .orElseThrow(() -> new IllegalArgumentException("not a commit: " + targetCommitId));

        Map<String, Index.Entry> previous = index.entries();
        Map<String, Index.Entry> next = flattenTree(target.treeId());

        for (String path : previous.keySet()) {
            if (!next.containsKey(path)) {
                try {
                    Files.deleteIfExists(workingDir.resolve(path));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        for (Map.Entry<String, Index.Entry> entry : next.entrySet()) {
            writeWorkingFile(entry.getKey(), entry.getValue());
        }
        index.replaceAll(next);

        if (refStore.exists(BRANCH_PREFIX + ref)) {
            head.pointToBranch(BRANCH_PREFIX + ref);
        } else {
            head.detachTo(targetCommitId);
        }
    }

    private void writeWorkingFile(String path, Index.Entry entry) {
        try {
            Path file = workingDir.resolve(path);
            Files.createDirectories(file.getParent());
            Blob blob = (Blob) objectStore.get(entry.blobId()).orElseThrow();
            Files.write(file, blob.content());
            if (entry.mode() == FileMode.EXECUTABLE_FILE) {
                file.toFile().setExecutable(true);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Index.Entry> flattenTree(ObjectId treeId) {
        Map<String, Index.Entry> result = new LinkedHashMap<>();
        flattenTree(treeId, "", result);
        return result;
    }

    private void flattenTree(ObjectId treeId, String prefix, Map<String, Index.Entry> out) {
        Tree tree = (Tree) objectStore.get(treeId).orElseThrow();
        for (TreeEntry entry : tree.entries()) {
            String path = prefix.isEmpty() ? entry.name() : prefix + "/" + entry.name();
            if (entry.isDirectory()) {
                flattenTree(entry.id(), path, out);
            } else {
                out.put(path, new Index.Entry(entry.mode(), entry.id()));
            }
        }
    }

    /** The line-level diff between two commits' trees. */
    public List<FileDiff> diff(ObjectId commitA, ObjectId commitB) {
        Commit a = (Commit) objectStore.get(commitA).orElseThrow();
        Commit b = (Commit) objectStore.get(commitB).orElseThrow();
        return TreeDiff.diff(objectStore, a.treeId(), b.treeId());
    }

    /**
     * Merges {@code theirRef} into HEAD. Fast-forwards when possible; otherwise
     * performs a three-way merge (with the recursive strategy for criss-cross
     * history) and, if clean, creates a merge commit with both parents and
     * materializes the result into the working directory. A conflicted merge
     * still materializes the best-effort merged tree so conflicts can be inspected
     * and resolved, but does not create a commit.
     */
    public MergeEngine.Outcome merge(String theirRef, PersonIdent author, PersonIdent committer, String message) {
        ObjectId ours = head.resolve().orElseThrow(() -> new IllegalStateException("HEAD has no commit yet"));
        ObjectId theirs = resolveCommitish(theirRef);
        MergeEngine.Outcome outcome = new MergeEngine(objectStore, generations).merge(ours, theirs);

        if (outcome.alreadyUpToDate()) {
            return outcome;
        }
        if (outcome.fastForward()) {
            Map<String, Index.Entry> next = flattenTree(outcome.mergedTreeId());
            next.forEach(this::writeWorkingFile);
            index.replaceAll(next);
            head.advance(theirs);
            return outcome;
        }

        Map<String, Index.Entry> next = flattenTree(outcome.mergedTreeId());
        next.forEach(this::writeWorkingFile);
        index.replaceAll(next);

        if (outcome.isClean()) {
            Commit commit = new Commit(outcome.mergedTreeId(), List.of(ours, theirs), author, committer, message);
            ObjectId commitId = objectStore.put(commit);
            GenerationNumbers.computeAndStore(objectStore, generations, commitId);
            head.advance(commitId);
        }
        return outcome;
    }

    public GenerationStore generations() {
        return generations;
    }
}
