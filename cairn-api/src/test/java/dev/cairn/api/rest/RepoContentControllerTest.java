package dev.cairn.api.rest;

import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.git.RepositoryRegistry;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.api.repo.UserJpaRepository;
import dev.cairn.vcs.dag.GenerationNumbers;
import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RepoContentControllerTest {

    @TempDir
    static Path reposDir;

    @DynamicPropertySource
    static void repoDir(DynamicPropertyRegistry registry) {
        registry.add("cairn.repos-dir", () -> reposDir.toString());
    }

    @LocalServerPort
    int port;

    @Autowired
    RepoJpaRepository repos;

    @Autowired
    UserJpaRepository users;

    @Autowired
    RepositoryRegistry repositories;

    private final TestRestTemplate rest = new TestRestTemplate();
    private static final PersonIdent PERSON = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");
    private static final java.util.concurrent.atomic.AtomicInteger COUNTER = new java.util.concurrent.atomic.AtomicInteger();

    private String ownerName;
    private String repoName;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private void seed() {
        int n = COUNTER.incrementAndGet();
        ownerName = "browse-owner-" + n;
        repoName = "browse-demo-" + n;
        User owner = users.save(new User(ownerName, "owner" + n + "@cairn.dev", ""));
        Repo repo = repos.save(new Repo(repoName, owner, null, Visibility.PUBLIC));

        var handle = repositories.resolve(ownerName, repoName);
        var store = handle.objectStore();
        ObjectId readme = store.put(new Blob("# Hello\n".getBytes()));
        ObjectId srcFile = store.put(new Blob("class App {}\n".getBytes()));
        ObjectId srcTree = store.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "App.java", srcFile))));
        ObjectId rootTree = store.put(new Tree(List.of(
                new TreeEntry(FileMode.REGULAR_FILE, "README.md", readme),
                new TreeEntry(FileMode.DIRECTORY, "src", srcTree))));
        ObjectId commitId = store.put(new Commit(rootTree, List.of(), PERSON, PERSON, "initial\n"));
        GenerationNumbers.computeAndStore(store, handle.generations(), commitId);
        handle.refStore().update("refs/heads/main", commitId);
    }

    @Test
    void treeListsEntriesAtRoot() {
        seed();
        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName + "/" + repoName + "/tree/main/", List.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void treeListsEntriesInASubdirectory() {
        seed();
        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName + "/" + repoName + "/tree/main/src", List.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void blobReturnsFileContent() {
        seed();
        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName + "/" + repoName + "/blob/main/README.md", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().get("content")).isEqualTo("# Hello\n");
    }

    @Test
    void blameAttributesEveryLineToTheSeedCommit() {
        seed();
        var handle = repositories.resolve(ownerName, repoName);
        String sha = handle.refStore().resolve("refs/heads/main").orElseThrow().hex();

        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName + "/" + repoName + "/blame/main/README.md", List.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(1);
        Map<?, ?> line = (Map<?, ?>) response.getBody().get(0);
        assertThat(line.get("commitId")).isEqualTo(sha);
        assertThat(line.get("line")).isEqualTo("# Hello\n");
    }

    @Test
    void blameOnANonexistentPathIsNotFound() {
        seed();
        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName + "/" + repoName + "/blame/main/nope.txt", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void commitsReturnsHistory() {
        seed();
        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName + "/" + repoName + "/commits/main", List.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void commitDiffShowsAddedFilesForARootCommit() {
        seed();
        var handle = repositories.resolve(ownerName, repoName);
        String sha = handle.refStore().resolve("refs/heads/main").orElseThrow().hex();

        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName + "/" + repoName + "/commit/" + sha, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<?> diffs = (List<?>) response.getBody().get("diffs");
        assertThat(diffs).hasSize(2);
    }

    @Test
    void compareReturnsTheDiffAndCommitsBetweenTwoRefs() {
        seed();
        var handle = repositories.resolve(ownerName, repoName);
        var store = handle.objectStore();
        ObjectId mainTip = handle.refStore().resolve("refs/heads/main").orElseThrow();
        Commit mainCommit = (Commit) store.get(mainTip).orElseThrow();

        ObjectId featureBlob = store.put(new Blob("feature content\n".getBytes()));
        ObjectId featureTree = store.put(new Tree(List.of(
                new TreeEntry(FileMode.REGULAR_FILE, "README.md", ((Tree) store.get(mainCommit.treeId()).orElseThrow())
                        .entry("README.md").orElseThrow().id()),
                new TreeEntry(FileMode.DIRECTORY, "src", ((Tree) store.get(mainCommit.treeId()).orElseThrow())
                        .entry("src").orElseThrow().id()),
                new TreeEntry(FileMode.REGULAR_FILE, "feature.txt", featureBlob))));
        ObjectId featureCommit = store.put(new Commit(featureTree, List.of(mainTip), PERSON, PERSON, "add feature\n"));
        GenerationNumbers.computeAndStore(store, handle.generations(), featureCommit);
        handle.refStore().update("refs/heads/feature", featureCommit);

        var response = rest.getForEntity(
                baseUrl() + "/api/repos/" + ownerName + "/" + repoName + "/compare/main...feature", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<?> commits = (List<?>) response.getBody().get("commits");
        List<?> diffs = (List<?>) response.getBody().get("diffs");
        assertThat(commits).hasSize(1);
        assertThat(diffs).hasSize(1);
        assertThat(((Map<?, ?>) diffs.get(0)).get("path")).isEqualTo("feature.txt");
    }

    @Test
    void branchesListsEveryRefsHeadsEntryWithItsTip() {
        seed();
        var handle = repositories.resolve(ownerName, repoName);
        String mainTip = handle.refStore().resolve("refs/heads/main").orElseThrow().hex();

        ObjectId featureCommit = handle.refStore().resolve("refs/heads/main").orElseThrow();
        handle.refStore().update("refs/heads/feature", featureCommit);

        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName + "/" + repoName + "/branches", List.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) (List<?>) response.getBody();
        var names = branches.stream().map(b -> b.get("name")).toList();
        assertThat(names).containsExactlyInAnyOrder("main", "feature");
        Map<String, Object> main = branches.stream().filter(b -> b.get("name").equals("main")).findFirst().orElseThrow();
        assertThat(main.get("tip")).isEqualTo(mainTip);
    }

    @Test
    void branchesOnAPrivateRepoIsDeniedAnonymously() {
        User owner = users.save(new User("hidden-branches-owner", "hb@cairn.dev", ""));
        repos.save(new Repo("hidden-branches-repo", owner, null, Visibility.PRIVATE));
        repositories.resolve("hidden-branches-owner", "hidden-branches-repo");

        var response = rest.getForEntity(
                baseUrl() + "/api/repos/hidden-branches-owner/hidden-branches-repo/branches", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void statsReportsBranchCountCommitCountAndLanguageBreakdown() {
        seed();
        var handle = repositories.resolve(ownerName, repoName);
        ObjectId mainTip = handle.refStore().resolve("refs/heads/main").orElseThrow();
        handle.refStore().update("refs/heads/feature", mainTip);

        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName + "/" + repoName + "/stats", Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().get("branchCount")).isEqualTo(2);
        assertThat(response.getBody().get("commitCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Number> languages = (Map<String, Number>) response.getBody().get("languages");
        // seed() writes README.md (Markdown) and src/App.java (Java).
        assertThat(languages).containsKeys("Markdown", "Java");
        assertThat(languages.get("Java").longValue()).isEqualTo("class App {}\n".getBytes().length);
    }

    @Test
    void statsOnARepoWithNoCommitsReturnsZeroedStatsRatherThanErroring() {
        User owner = users.save(new User("empty-stats-owner", "es@cairn.dev", ""));
        repos.save(new Repo("empty-stats-repo", owner, null, Visibility.PUBLIC));
        repositories.resolve("empty-stats-owner", "empty-stats-repo");

        var response = rest.getForEntity(baseUrl() + "/api/repos/empty-stats-owner/empty-stats-repo/stats", Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().get("branchCount")).isEqualTo(0);
        assertThat(response.getBody().get("commitCount")).isEqualTo(0);
    }

    @Test
    void statsOnAPrivateRepoIsDeniedAnonymously() {
        User owner = users.save(new User("hidden-stats-owner", "hs@cairn.dev", ""));
        repos.save(new Repo("hidden-stats-repo", owner, null, Visibility.PRIVATE));
        repositories.resolve("hidden-stats-owner", "hidden-stats-repo");

        var response = rest.getForEntity(
                baseUrl() + "/api/repos/hidden-stats-owner/hidden-stats-repo/stats", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void privateRepoBrowsingIsDeniedAnonymously() {
        User owner = users.save(new User("hidden-owner", "hidden@cairn.dev", ""));
        repos.save(new Repo("hidden-repo", owner, null, Visibility.PRIVATE));
        repositories.resolve("hidden-owner", "hidden-repo");

        var response = rest.getForEntity(baseUrl() + "/api/repos/hidden-owner/hidden-repo/tree/main/", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void browsingABrandNewRepoWithNoCommitsReturnsEmptyRatherThanErroring() {
        // Regression test: resolving "main" on a repo with zero commits used to
        // fall through to parsing "main" as a hex object id and throw, instead of
        // being treated as the legitimate "nothing pushed yet" case.
        User owner = users.save(new User("empty-owner", "empty@cairn.dev", ""));
        repos.save(new Repo("empty-repo", owner, null, Visibility.PUBLIC));
        repositories.resolve("empty-owner", "empty-repo");

        var treeResponse = rest.getForEntity(baseUrl() + "/api/repos/empty-owner/empty-repo/tree/main/", List.class);
        assertThat(treeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(treeResponse.getBody()).isEmpty();

        var commitsResponse = rest.getForEntity(baseUrl() + "/api/repos/empty-owner/empty-repo/commits/main", List.class);
        assertThat(commitsResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(commitsResponse.getBody()).isEmpty();

        var blobResponse = rest.getForEntity(baseUrl() + "/api/repos/empty-owner/empty-repo/blob/main/README.md", String.class);
        assertThat(blobResponse.getStatusCode().value()).isEqualTo(404);
    }
}
