package dev.cairn.api.rest;

import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.git.RepositoryRegistry;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.vcs.dag.RevWalk;
import dev.cairn.vcs.diff.FileDiff;
import dev.cairn.vcs.diff.Lines;
import dev.cairn.vcs.diff.TreeDiff;
import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.store.ObjectStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read endpoints the web UI (M8) browses with: tree listing, file content, commit
 * history, and a single commit's diff (architecture doc, section 7.1). Every path
 * gates on {@code effective_role >= read}, exactly like the Git HTTP endpoints.
 */
@RestController
@RequestMapping("/api/repos/{owner}/{name}")
public class RepoContentController {

    private final RepoJpaRepository repos;
    private final RepositoryRegistry repositories;
    private final PrincipalResolver principalResolver;
    private final PermissionResolver permissionResolver;

    public RepoContentController(RepoJpaRepository repos, RepositoryRegistry repositories,
                                  PrincipalResolver principalResolver, PermissionResolver permissionResolver) {
        this.repos = repos;
        this.repositories = repositories;
        this.principalResolver = principalResolver;
        this.permissionResolver = permissionResolver;
    }

    public record TreeEntryView(String name, String mode, String kind, String id) {
    }

    public record CommitView(String id, String message, String authorName, String authorEmail, long authorTime,
                              List<String> parents) {
    }

    private Repo requireReadableRepo(String owner, String name, HttpServletRequest request) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        if (repo == null) {
            return null;
        }
        Principal principal = principalResolver.resolve(request);
        return permissionResolver.authorize(principal, repo, Role.READ) ? repo : null;
    }

    /**
     * Resolves a ref name (a branch, or a raw commit id) to a commit id. Empty, not
     * a thrown error, if the ref simply doesn't exist yet: the common and legitimate
     * case of a freshly created repository with no commits at all, which every
     * caller here treats as "empty," not "broken."
     */
    private Optional<ObjectId> resolveRef(RepositoryRegistry.RepositoryHandle handle, String ref) {
        return handle.refStore().resolve("refs/heads/" + ref)
                .or(() -> handle.refStore().resolve(ref))
                .or(() -> {
                    try {
                        ObjectId id = ObjectId.fromHex(ref);
                        return handle.objectStore().has(id) ? Optional.of(id) : Optional.empty();
                    } catch (RuntimeException e) {
                        return Optional.empty();
                    }
                });
    }

    @GetMapping("/tree/{ref}/**")
    public ResponseEntity<?> tree(@PathVariable String owner, @PathVariable String name, @PathVariable String ref,
                                   HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        var handle = repositories.resolve(owner, name);
        String path = extractPath(request, ref);

        Optional<ObjectId> commitId = resolveRef(handle, ref);
        if (commitId.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        Commit commit = (Commit) handle.objectStore().get(commitId.get()).orElseThrow();
        ObjectId treeId = navigateToTree(handle.objectStore(), commit.treeId(), path);
        Tree tree = (Tree) handle.objectStore().get(treeId).orElseThrow();

        List<TreeEntryView> entries = tree.entries().stream()
                .map(e -> new TreeEntryView(e.name(), e.mode().octal(), e.isDirectory() ? "tree" : "blob", e.id().hex()))
                .toList();
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/blob/{ref}/**")
    public ResponseEntity<?> blob(@PathVariable String owner, @PathVariable String name, @PathVariable String ref,
                                   HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        var handle = repositories.resolve(owner, name);
        String path = extractPath(request, ref);

        Optional<ObjectId> commitId = resolveRef(handle, ref);
        if (commitId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Commit commit = (Commit) handle.objectStore().get(commitId.get()).orElseThrow();
        ObjectId blobId = navigateToBlob(handle.objectStore(), commit.treeId(), path);
        if (blobId == null) {
            return ResponseEntity.notFound().build();
        }
        Blob blob = (Blob) handle.objectStore().get(blobId).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "path", path,
                "content", new String(blob.content(), StandardCharsets.UTF_8),
                "size", blob.size()));
    }

    @GetMapping("/commits/{ref}")
    public ResponseEntity<?> commits(@PathVariable String owner, @PathVariable String name, @PathVariable String ref,
                                      HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        var handle = repositories.resolve(owner, name);
        Optional<ObjectId> commitId = resolveRef(handle, ref);
        if (commitId.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<Commit> history = new RevWalk(handle.objectStore()).history(List.of(commitId.get()));
        return ResponseEntity.ok(history.stream().map(this::toView).toList());
    }

    public record EditView(String type, int origStart, int origEnd, int revStart, int revEnd) {
    }

    public record FileDiffView(String path, String kind, List<String> oldLines, List<String> newLines, List<EditView> edits) {
    }

    @GetMapping("/commit/{sha}")
    public ResponseEntity<?> commitDiff(@PathVariable String owner, @PathVariable String name, @PathVariable String sha,
                                         HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        var handle = repositories.resolve(owner, name);
        ObjectStore store = handle.objectStore();
        Commit commit = (Commit) store.get(ObjectId.fromHex(sha)).orElseThrow();

        ObjectId baseTree = commit.isRoot() ? store.put(new Tree(List.of()))
                : ((Commit) store.get(commit.parents().get(0)).orElseThrow()).treeId();
        List<FileDiff> diffs = TreeDiff.diff(store, baseTree, commit.treeId());

        List<FileDiffView> views = diffs.stream().map(d -> toFileDiffView(store, baseTree, commit.treeId(), d)).toList();
        return ResponseEntity.ok(Map.of("commit", toView(commit), "diffs", views));
    }

    private FileDiffView toFileDiffView(ObjectStore store, ObjectId baseTree, ObjectId revTree, FileDiff diff) {
        List<String> oldLines = readLines(store, baseTree, diff.path());
        List<String> newLines = readLines(store, revTree, diff.path());
        List<EditView> edits = diff.edits().stream()
                .map(e -> new EditView(e.type().name(), e.origStart(), e.origEnd(), e.revStart(), e.revEnd()))
                .toList();
        return new FileDiffView(diff.path(), diff.kind().name(), oldLines, newLines, edits);
    }

    private List<String> readLines(ObjectStore store, ObjectId treeId, String path) {
        ObjectId blobId = navigateToBlob(store, treeId, path);
        if (blobId == null) {
            return List.of();
        }
        Blob blob = (Blob) store.get(blobId).orElseThrow();
        return Lines.of(blob.content());
    }

    private CommitView toView(Commit commit) {
        return new CommitView(commit.id().hex(), commit.message(), commit.author().name(), commit.author().email(),
                commit.author().epochSeconds(), commit.parents().stream().map(ObjectId::hex).toList());
    }

    private String extractPath(HttpServletRequest request, String ref) {
        String uri = request.getRequestURI();
        int marker = uri.indexOf("/" + ref + "/");
        return marker < 0 ? "" : uri.substring(marker + ref.length() + 2);
    }

    private ObjectId navigateToTree(ObjectStore store, ObjectId treeId, String path) {
        if (path.isEmpty()) {
            return treeId;
        }
        ObjectId current = treeId;
        for (String segment : path.split("/")) {
            Tree tree = (Tree) store.get(current).orElseThrow();
            TreeEntry entry = tree.entry(segment).orElseThrow(() -> new IllegalArgumentException("path not found: " + path));
            current = entry.id();
        }
        return current;
    }

    /**
     * Returns null (not a thrown error) for any segment of the path that doesn't
     * exist, not just the final one: a diff routinely asks for a path on the side
     * that added or deleted it, where an intermediate directory may genuinely be
     * absent (a file added in a brand-new subdirectory has no such directory in the
     * base tree at all).
     */
    private ObjectId navigateToBlob(ObjectStore store, ObjectId treeId, String path) {
        String[] segments = path.split("/");
        ObjectId current = treeId;
        for (int i = 0; i < segments.length - 1; i++) {
            Tree tree = (Tree) store.get(current).orElseThrow();
            var entry = tree.entry(segments[i]);
            if (entry.isEmpty()) {
                return null;
            }
            current = entry.get().id();
        }
        Tree tree = (Tree) store.get(current).orElseThrow();
        return tree.entry(segments[segments.length - 1]).map(TreeEntry::id).orElse(null);
    }
}
