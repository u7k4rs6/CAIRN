package dev.cairn.api.rest;

import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.git.RepositoryRegistry;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.vcs.blame.Blame;
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
import java.util.LinkedHashMap;
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

    public record BlameLineView(int lineNumber, String line, String commitId) {
    }

    /** FR-BROWSE-1: each line attributed to the commit that last changed it (a real engine feature, {@link Blame}, not a stub). */
    @GetMapping("/blame/{ref}/**")
    public ResponseEntity<?> blame(@PathVariable String owner, @PathVariable String name, @PathVariable String ref,
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
        List<Blame.LineBlame> lines = Blame.blame(handle.objectStore(), commitId.get(), path);
        List<BlameLineView> views = lines.stream()
                .map(l -> new BlameLineView(l.lineNumber(), l.line(), l.commitId().hex()))
                .toList();
        return ResponseEntity.ok(views);
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

    /**
     * Frontend spec route {@code /{owner}/{repo}/compare/{base}...{head}}: the Files
     * changed and Commits tabs both need this (M8's DECISIONS.md named it as the
     * missing piece blocking them, "one commit vs. its first parent" from
     * {@code /commit/{sha}} is not the same as "base ref vs. head ref").
     */
    @GetMapping("/compare/{base}...{head}")
    public ResponseEntity<?> compare(@PathVariable String owner, @PathVariable String name,
                                      @PathVariable String base, @PathVariable String head,
                                      HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        var handle = repositories.resolve(owner, name);
        ObjectStore store = handle.objectStore();

        Optional<ObjectId> baseCommitId = resolveRef(handle, base);
        Optional<ObjectId> headCommitId = resolveRef(handle, head);
        if (baseCommitId.isEmpty() || headCommitId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ObjectId baseTree = ((Commit) store.get(baseCommitId.get()).orElseThrow()).treeId();
        ObjectId headTree = ((Commit) store.get(headCommitId.get()).orElseThrow()).treeId();
        List<FileDiff> diffs = TreeDiff.diff(store, baseTree, headTree);
        List<FileDiffView> diffViews = diffs.stream().map(d -> toFileDiffView(store, baseTree, headTree, d)).toList();

        var baseReachable = new RevWalk(store).reachableFrom(baseCommitId.get());
        List<CommitView> commitViews = new RevWalk(store).history(List.of(headCommitId.get())).stream()
                .filter(c -> !baseReachable.contains(c.id()))
                .map(this::toView)
                .toList();

        return ResponseEntity.ok(Map.of("commits", commitViews, "diffs", diffViews));
    }

    private static final String DEFAULT_BRANCH = "main";

    public record BranchView(String name, String tip) {
    }

    /** A repo's branches (not tags: only {@code refs/heads/*}), name and tip commit id. No caching (docs/HLD.md): a fresh {@code RefStore.list()} call every time. */
    @GetMapping("/branches")
    public ResponseEntity<?> branches(@PathVariable String owner, @PathVariable String name, HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        var handle = repositories.resolve(owner, name);
        List<BranchView> views = handle.refStore().list().entrySet().stream()
                .filter(e -> e.getKey().startsWith("refs/heads/"))
                .map(e -> new BranchView(e.getKey().substring("refs/heads/".length()), e.getValue().hex()))
                .toList();
        return ResponseEntity.ok(views);
    }

    /**
     * A small built-in extension-to-language map (no external linguist dependency,
     * per this endpoint's own scope note). Deliberately short: this is a repo-stats
     * summary, not an attempt at GitHub's own exhaustive language detection.
     */
    private static final Map<String, String> LANGUAGE_BY_EXTENSION = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"),
            Map.entry("js", "JavaScript"), Map.entry("jsx", "JavaScript"), Map.entry("mjs", "JavaScript"), Map.entry("cjs", "JavaScript"),
            Map.entry("ts", "TypeScript"), Map.entry("tsx", "TypeScript"),
            Map.entry("py", "Python"), Map.entry("rb", "Ruby"), Map.entry("php", "PHP"),
            Map.entry("go", "Go"), Map.entry("rs", "Rust"),
            Map.entry("c", "C"), Map.entry("h", "C"), Map.entry("cpp", "C++"), Map.entry("cc", "C++"), Map.entry("hpp", "C++"),
            Map.entry("cs", "C#"), Map.entry("swift", "Swift"),
            Map.entry("html", "HTML"), Map.entry("htm", "HTML"), Map.entry("css", "CSS"), Map.entry("scss", "CSS"),
            Map.entry("sh", "Shell"), Map.entry("bash", "Shell"),
            Map.entry("sql", "SQL"), Map.entry("md", "Markdown"), Map.entry("markdown", "Markdown"),
            Map.entry("json", "JSON"), Map.entry("yml", "YAML"), Map.entry("yaml", "YAML"), Map.entry("xml", "XML"),
            Map.entry("gradle", "Gradle"));

    private static String languageOf(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "Other";
        }
        return LANGUAGE_BY_EXTENSION.getOrDefault(path.substring(dot + 1).toLowerCase(), "Other");
    }

    /**
     * Walks every blob reachable from {@code treeId}, summing byte size per
     * language. This is the same shape of full recursive tree walk
     * {@code ObjectClosure.walkTree} (cairn-transfer) already does for negotiation,
     * just accumulating sizes instead of collecting ids; not shared code, since
     * cairn-api has no dependency on cairn-transfer's package-private internals and
     * this session doesn't touch that module.
     */
    private void accumulateLanguages(ObjectStore store, ObjectId treeId, String pathPrefix, Map<String, Long> totals) {
        Tree tree = (Tree) store.get(treeId).orElseThrow();
        for (TreeEntry entry : tree.entries()) {
            String path = pathPrefix.isEmpty() ? entry.name() : pathPrefix + "/" + entry.name();
            if (entry.isDirectory()) {
                accumulateLanguages(store, entry.id(), path, totals);
            } else {
                Blob blob = (Blob) store.get(entry.id()).orElseThrow();
                totals.merge(languageOf(path), (long) blob.size(), Long::sum);
            }
        }
    }

    public record RepoStatsView(int branchCount, int commitCount, Map<String, Long> languages) {
    }

    /**
     * Branch count (ref count), commit count (a full {@link RevWalk} from the
     * default branch tip), and a language breakdown (bytes per language, summed
     * over every blob in the default branch's tree). Uncached, consistent with
     * docs/HLD.md's documented tradeoff for this scale of deployment - a real
     * bottleneck at a much bigger repo size, not solved here.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> stats(@PathVariable String owner, @PathVariable String name, HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        var handle = repositories.resolve(owner, name);
        ObjectStore store = handle.objectStore();
        int branchCount = (int) handle.refStore().list().keySet().stream()
                .filter(k -> k.startsWith("refs/heads/"))
                .count();

        Optional<ObjectId> defaultTip = resolveRef(handle, DEFAULT_BRANCH);
        if (defaultTip.isEmpty()) {
            return ResponseEntity.ok(new RepoStatsView(branchCount, 0, Map.of()));
        }
        int commitCount = new RevWalk(store).history(List.of(defaultTip.get())).size();

        Commit tip = (Commit) store.get(defaultTip.get()).orElseThrow();
        Map<String, Long> languages = new LinkedHashMap<>();
        accumulateLanguages(store, tip.treeId(), "", languages);

        return ResponseEntity.ok(new RepoStatsView(branchCount, commitCount, languages));
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
