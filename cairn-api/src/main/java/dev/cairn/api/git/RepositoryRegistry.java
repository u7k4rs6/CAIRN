package dev.cairn.api.git;

import dev.cairn.vcs.dag.FileGenerationStore;
import dev.cairn.vcs.dag.GenerationStore;
import dev.cairn.vcs.ref.FileRefStore;
import dev.cairn.vcs.ref.RefStore;
import dev.cairn.vcs.store.LooseObjectStore;
import dev.cairn.vcs.store.ObjectStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Resolves an {@code {owner}/{repo}} path segment to its on-disk storage: a bare
 * object/ref/generation store with no working tree, since the server never checks
 * files out (only Git clients materialize a working tree).
 *
 * <p><b>Staged scope.</b> Auto-creates a repository on first access. Real repository
 * creation, ownership, and visibility (FR-REPO-1) are M6/M7's job; this is
 * deliberately the simplest thing that lets M5's transfer protocol have somewhere to
 * read from and write to.
 */
@Component
public class RepositoryRegistry {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

    private final Path baseDir;
    private final ConcurrentHashMap<String, RepositoryHandle> cache = new ConcurrentHashMap<>();

    public RepositoryRegistry(@Value("${cairn.repos-dir:./data/repos}") String reposDir) {
        this.baseDir = Path.of(reposDir).toAbsolutePath().normalize();
    }

    public record RepositoryHandle(ObjectStore objectStore, RefStore refStore, GenerationStore generations) {
    }

    public RepositoryHandle resolve(String owner, String repo) {
        validateSegment(owner);
        validateSegment(repo);
        String key = owner + "/" + repo;
        return cache.computeIfAbsent(key, k -> {
            // Names are validated above and never used to build the path beyond a
            // direct child-directory join, so this cannot escape baseDir (security
            // doc, section 6.3: resolve names to ids/safe paths, never raw concatenation).
            Path dir = baseDir.resolve(owner).resolve(repo);
            return new RepositoryHandle(
                    new LooseObjectStore(dir.resolve("objects")),
                    new FileRefStore(dir.resolve("refs")),
                    new FileGenerationStore(dir.resolve("generations")));
        });
    }

    private void validateSegment(String segment) {
        if (!SAFE_SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException("invalid repository path segment: " + segment);
        }
    }
}
