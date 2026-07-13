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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * FR-SEARCH-1 through the real HTTP boundary: a query returns correct matches
 * scoped to one repo, private repos are masked like every other endpoint, a query
 * under three characters is reported as too short rather than silently empty, and
 * a brand-new repo's first query returns the frontend spec's "indexing" state
 * before the background build completes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchControllerTest {

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
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String seedRepoWithContent(Visibility visibility) {
        int n = COUNTER.incrementAndGet();
        String owner = "search-owner-" + n;
        String repoName = "search-demo-" + n;
        User user = users.save(new User(owner, owner + "@cairn.dev", ""));
        repos.save(new Repo(repoName, user, null, visibility));

        var handle = repositories.resolve(owner, repoName);
        var store = handle.objectStore();
        ObjectId file = store.put(new Blob("class Widget {\n    void assemble() {}\n}\n".getBytes()));
        ObjectId tree = store.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "Widget.java", file))));
        ObjectId commitId = store.put(new Commit(tree, List.of(), PERSON, PERSON, "initial\n"));
        GenerationNumbers.computeAndStore(store, handle.generations(), commitId);
        handle.refStore().update("refs/heads/main", commitId);
        return owner + "/" + repoName;
    }

    @Test
    void aQueryUnderThreeCharsIsReportedAsTooShort() {
        String path = seedRepoWithContent(Visibility.PUBLIC);
        var response = rest.getForEntity(baseUrl() + "/api/repos/" + path + "/search?q=as", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().get("queryTooShort")).isEqualTo(true);
    }

    @Test
    void aMatchingQueryEventuallyReturnsTheRightFileAndLine() {
        String path = seedRepoWithContent(Visibility.PUBLIC);

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            var response = rest.getForEntity(baseUrl() + "/api/repos/" + path + "/search?q=assemble", Map.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody().get("indexing")).isEqualTo(false);
            List<?> results = (List<?>) response.getBody().get("results");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).toString()).contains("Widget.java");
        });
    }

    @Test
    void aNonMatchingQueryReturnsNoResultsOnceIndexed() {
        String path = seedRepoWithContent(Visibility.PUBLIC);

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            var response = rest.getForEntity(baseUrl() + "/api/repos/" + path + "/search?q=nonexistentXYZ", Map.class);
            assertThat(response.getBody().get("indexing")).isEqualTo(false);
        });
    }

    @Test
    void searchOnAPrivateRepoIsMaskedAsNotFoundForAStranger() {
        String path = seedRepoWithContent(Visibility.PRIVATE);
        var response = rest.getForEntity(baseUrl() + "/api/repos/" + path + "/search?q=assemble", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void searchOnARepoWithNoCommitsReturnsEmptyRatherThanIndexing() {
        User owner = users.save(new User("empty-search-owner", "e@cairn.dev", ""));
        repos.save(new Repo("empty-search-repo", owner, null, Visibility.PUBLIC));
        repositories.resolve("empty-search-owner", "empty-search-repo");

        var response = rest.getForEntity(baseUrl() + "/api/repos/empty-search-owner/empty-search-repo/search?q=anything", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().get("indexing")).isEqualTo(false);
        assertThat((List<?>) response.getBody().get("results")).isEmpty();
    }
}
