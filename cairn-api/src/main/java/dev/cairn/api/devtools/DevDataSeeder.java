package dev.cairn.api.devtools;

import dev.cairn.api.auth.TokenHasher;
import dev.cairn.api.collab.Comment;
import dev.cairn.api.collab.Issue;
import dev.cairn.api.collab.PullRequest;
import dev.cairn.api.collab.Review;
import dev.cairn.api.collab.ReviewVerdict;
import dev.cairn.api.domain.PersonalAccessToken;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.git.RepositoryRegistry;
import dev.cairn.api.repo.CommentJpaRepository;
import dev.cairn.api.repo.IssueJpaRepository;
import dev.cairn.api.repo.PersonalAccessTokenJpaRepository;
import dev.cairn.api.repo.PullRequestJpaRepository;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.api.repo.ReviewJpaRepository;
import dev.cairn.api.repo.UserJpaRepository;
import dev.cairn.vcs.dag.GenerationNumbers;
import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * A local "quickstart" for M8: seeds one demo repo with a small commit history, an
 * issue, and a pull request, and prints a working personal access token so the web
 * UI has something real to browse and act on. Only runs under the {@code seed}
 * profile; never active in tests or a normal run (`./gradlew :cairn-api:bootRun
 * --args='--spring.profiles.active=seed'`).
 *
 * <p><b>Self-healing, not just idempotent.</b> This runs on every boot under the
 * {@code seed} profile, and the database and the git object store
 * ({@code cairn.repos-dir}) are two independent stores that do not fail together:
 * a platform that gives the database durable storage but the app's own disk
 * ephemeral storage (the exact shape of this project's own Railway deploy) can
 * restart with the Postgres rows from a previous successful seed still present
 * while the git objects/refs are gone. The original version of this class keyed
 * everything off "does the DB row exist" and unconditionally called
 * {@code .save(new User(...))} et al. on every boot; against a database that
 * already had that row, that threw a duplicate-key violation on the very first
 * line, before ever reaching the git-object-writing code below - so a wiped disk
 * with a surviving database never got its git content rewritten, and
 * {@code acme/demo} cloned empty. Every write here is now framed as "ensure this
 * exists," checked independently per store: JPA rows are found-or-created (never
 * blindly inserted), and the git content is rewritten whenever it is actually
 * missing - resolving {@code refs/heads/main} and confirming the object it points
 * to is actually present, not just assuming a resolvable ref means the store is
 * intact. Re-running the git-write path is itself safe even if only some of it
 * needed repair: {@link dev.cairn.vcs.store.ObjectStore#put} is content-addressed
 * (identical bytes hash to the identical id and are deduped, not re-inserted) and
 * {@link dev.cairn.vcs.ref.RefStore#update} is a plain overwrite, so replaying the
 * exact same seed content is a no-op wherever it already matches.
 */
@Component
@Profile("seed")
public class DevDataSeeder implements CommandLineRunner {

    private static final PersonIdent PERSON = new PersonIdent("Ada Lovelace", "ada@cairn.dev", 1_700_000_000L, "+0000");

    private final UserJpaRepository users;
    private final RepoJpaRepository repos;
    private final RepositoryRegistry repositories;
    private final PersonalAccessTokenJpaRepository tokens;
    private final TokenHasher tokenHasher;
    private final IssueJpaRepository issues;
    private final PullRequestJpaRepository pullRequests;
    private final ReviewJpaRepository reviews;
    private final CommentJpaRepository comments;

    public DevDataSeeder(UserJpaRepository users, RepoJpaRepository repos, RepositoryRegistry repositories,
                          PersonalAccessTokenJpaRepository tokens, TokenHasher tokenHasher,
                          IssueJpaRepository issues, PullRequestJpaRepository pullRequests,
                          ReviewJpaRepository reviews, CommentJpaRepository comments) {
        this.users = users;
        this.repos = repos;
        this.repositories = repositories;
        this.tokens = tokens;
        this.tokenHasher = tokenHasher;
        this.issues = issues;
        this.pullRequests = pullRequests;
        this.reviews = reviews;
        this.comments = comments;
    }

    @Override
    public void run(String... args) {
        User owner = users.findByUsername("acme")
                .orElseGet(() -> users.save(new User("acme", "acme@cairn.dev", "")));
        Repo repo = repos.findByOwnerAndName("acme", "demo")
                .orElseGet(() -> repos.save(new Repo("demo", owner, null, Visibility.PUBLIC)));

        // The raw token behind an existing row can never be recovered (only its
        // hash is stored) - minted once, not reprinted, not reminted.
        String printedToken = null;
        if (!tokens.existsByUser(owner)) {
            printedToken = "seed-" + UUID.randomUUID();
            tokens.save(new PersonalAccessToken(owner, tokenHasher.hash(printedToken), "repo:write", null));
        }

        var handle = repositories.resolve("acme", "demo");
        var store = handle.objectStore();

        ObjectId existingMainTip = handle.refStore().resolve("refs/heads/main").orElse(null);
        boolean gitContentPresent = existingMainTip != null && store.has(existingMainTip);

        if (!gitContentPresent) {
            ObjectId readme = store.put(new Blob("# Cairn demo\n\nA seeded repository for trying the UI locally.\n".getBytes()));
            ObjectId appFile = store.put(new Blob("public class App {\n    // entry point\n}\n".getBytes()));
            ObjectId srcTree = store.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "App.java", appFile))));
            ObjectId rootTree = store.put(new Tree(List.of(
                    new TreeEntry(FileMode.REGULAR_FILE, "README.md", readme),
                    new TreeEntry(FileMode.DIRECTORY, "src", srcTree))));
            ObjectId first = store.put(new Commit(rootTree, List.of(), PERSON, PERSON, "initial commit\n"));
            GenerationNumbers.computeAndStore(store, handle.generations(), first);
            handle.refStore().update("refs/heads/main", first);

            ObjectId appFile2 = store.put(new Blob("public class App {\n    // entry point\n    // TODO: add logic\n}\n".getBytes()));
            ObjectId srcTree2 = store.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "App.java", appFile2))));
            ObjectId rootTree2 = store.put(new Tree(List.of(
                    new TreeEntry(FileMode.REGULAR_FILE, "README.md", readme),
                    new TreeEntry(FileMode.DIRECTORY, "src", srcTree2))));
            ObjectId second = store.put(new Commit(rootTree2, List.of(first), PERSON, PERSON, "add a TODO\n"));
            GenerationNumbers.computeAndStore(store, handle.generations(), second);
            handle.refStore().update("refs/heads/main", second);

            ObjectId featureFile = store.put(new Blob(
                    "# Cairn demo\n\nA seeded repository for trying the UI locally.\n\n## Feature branch change\n".getBytes()));
            ObjectId featureTree = store.put(new Tree(List.of(
                    new TreeEntry(FileMode.REGULAR_FILE, "README.md", featureFile),
                    new TreeEntry(FileMode.DIRECTORY, "src", srcTree2))));
            ObjectId featureCommit = store.put(new Commit(featureTree, List.of(second), PERSON, PERSON, "propose a README update\n"));
            GenerationNumbers.computeAndStore(store, handle.generations(), featureCommit);
            handle.refStore().update("refs/heads/feature", featureCommit);
        }

        // The PR/review/comment rows below reference these branch names and a
        // specific path/line, not object ids, so they stay valid regardless of
        // whether the git content above was just rewritten or was already there.
        User reviewer = users.findByUsername("reviewer")
                .orElseGet(() -> users.save(new User("reviewer", "reviewer@cairn.dev", "")));

        if (!issues.existsByRepoAndTitle(repo, "Example issue")) {
            issues.save(new Issue(repo, owner, "Example issue", "This is a seeded issue for trying the UI."));
        }

        PullRequest pr = pullRequests.findByRepoAndTitle(repo, "Example pull request")
                .orElseGet(() -> pullRequests.save(
                        new PullRequest(repo, owner, "Example pull request", "refs/heads/feature", "refs/heads/main")));

        List<Review> existingReviews = reviews.findByPullRequestOrderByIdAsc(pr);
        if (existingReviews.stream().noneMatch(r -> r.verdict() == ReviewVerdict.APPROVE)) {
            reviews.save(new Review(pr, reviewer, ReviewVerdict.APPROVE,
                    "Looks good overall, just one nit on the README.", null, null));
        }
        boolean hasReplyComment = comments.findByPullRequestOrderByIdAsc(pr).stream()
                .anyMatch(c -> "Thanks for the review!".equals(c.body()));
        if (!hasReplyComment) {
            comments.save(Comment.onPullRequest(pr, owner, "Thanks for the review!"));
        }
        // README.md's feature-branch version adds "## Feature branch change" as its
        // 5th line (see featureFile above) - a real changed line to anchor to.
        boolean hasLineComment = existingReviews.stream()
                .anyMatch(r -> "README.md".equals(r.path()) && Integer.valueOf(5).equals(r.line()));
        if (!hasLineComment) {
            reviews.save(new Review(pr, reviewer, ReviewVerdict.COMMENT,
                    "Consider a more specific heading here.", "README.md", 5));
        }

        System.out.println("=".repeat(60));
        System.out.println("Cairn dev data seeded" + (gitContentPresent ? "" : " (git content was missing - rewritten)") + ".");
        System.out.println("  Repo:     http://localhost:8080/acme/demo");
        System.out.println("  Username: acme");
        if (printedToken != null) {
            System.out.println("  Token:    " + printedToken);
        } else {
            System.out.println("  Token:    (already minted on a previous boot - not recoverable here; reuse the one printed then)");
        }
        System.out.println("=".repeat(60));
    }
}
