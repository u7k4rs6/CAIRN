package dev.cairn.api.rest;

import dev.cairn.api.auth.TokenHasher;
import dev.cairn.api.domain.PersonalAccessToken;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.git.RepositoryRegistry;
import dev.cairn.api.repo.PersonalAccessTokenJpaRepository;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Observer pattern's real end-to-end behavior (architecture doc, section 4):
 * opening an issue, opening a pull request, and merging one all fan out through
 * {@link dev.cairn.api.activity.ActivityPublisher} to
 * {@link dev.cairn.api.activity.InMemoryActivityFeed}, readable back through
 * {@link ActivityController}, gated by the same read permission as everything else.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivityFeedIntegrationTest {

    @TempDir
    static Path reposDir;

    @DynamicPropertySource
    static void repoDir(DynamicPropertyRegistry registry) {
        registry.add("cairn.repos-dir", () -> reposDir.toString());
    }

    @LocalServerPort
    int port;

    @Autowired
    UserJpaRepository users;

    @Autowired
    RepoJpaRepository repos;

    @Autowired
    PersonalAccessTokenJpaRepository tokens;

    @Autowired
    TokenHasher tokenHasher;

    @Autowired
    RepositoryRegistry repositories;

    private static final PersonIdent PERSON = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");
    private final TestRestTemplate rest = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders(String username, String rawToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, rawToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void openingAnIssueOpeningAPullRequestAndMergingItAllAppearInTheActivityFeedNewestFirst() {
        User owner = users.save(new User("acme-activity", "acme-activity@cairn.dev", ""));
        Repo repo = repos.save(new Repo("demo-activity", owner, null, Visibility.PUBLIC));
        String rawToken = "activity-token-" + UUID.randomUUID();
        tokens.save(new PersonalAccessToken(owner, tokenHasher.hash(rawToken), "repo:write", null));
        HttpHeaders headers = authHeaders("acme-activity", rawToken);

        var handle = repositories.resolve("acme-activity", "demo-activity");
        var store = handle.objectStore();
        ObjectId baseTree = store.put(new Tree(List.of()));
        ObjectId root = store.put(new Commit(baseTree, List.of(), PERSON, PERSON, "root"));
        GenerationNumbers.computeAndStore(store, handle.generations(), root);
        handle.refStore().update("refs/heads/main", root);

        ObjectId blob = store.put(new Blob("feature\n".getBytes()));
        ObjectId featureTree = store.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "feature.txt", blob))));
        ObjectId featureCommit = store.put(new Commit(featureTree, List.of(root), PERSON, PERSON, "feature work"));
        GenerationNumbers.computeAndStore(store, handle.generations(), featureCommit);
        handle.refStore().update("refs/heads/feature", featureCommit);

        // 1. Opening an issue publishes "issue_opened".
        var issueBody = "{\"title\":\"a bug\",\"body\":\"details\"}";
        var issueResponse = rest.exchange(baseUrl() + "/api/repos/acme-activity/demo-activity/issues",
                HttpMethod.POST, new HttpEntity<>(issueBody, headers), String.class);
        assertThat(issueResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // 2. Opening a pull request publishes "pr_opened".
        var prBody = "{\"title\":\"add feature\",\"sourceRef\":\"refs/heads/feature\",\"targetRef\":\"refs/heads/main\"}";
        var prResponse = rest.exchange(baseUrl() + "/api/repos/acme-activity/demo-activity/pulls",
                HttpMethod.POST, new HttpEntity<>(prBody, headers), Map.class);
        assertThat(prResponse.getStatusCode().is2xxSuccessful()).isTrue();
        Number prId = (Number) prResponse.getBody().get("id");

        // 3. Merging that pull request publishes "pr_merged".
        var mergeBody = "{\"strategy\":\"MERGE_COMMIT\",\"message\":\"merge it\"}";
        var mergeResponse = rest.exchange(
                baseUrl() + "/api/repos/acme-activity/demo-activity/pulls/" + prId + "/merge",
                HttpMethod.POST, new HttpEntity<>(mergeBody, headers), String.class);
        assertThat(mergeResponse.getStatusCode().is2xxSuccessful()).isTrue();

        var feedResponse = rest.exchange(baseUrl() + "/api/repos/acme-activity/demo-activity/activity",
                HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(feedResponse.getStatusCode().is2xxSuccessful()).isTrue();
        List<?> events = feedResponse.getBody();
        assertThat(events).hasSize(3);

        List<String> types = events.stream().map(e -> (String) ((Map<?, ?>) e).get("type")).toList();
        assertThat(types).containsExactly("pr_merged", "pr_opened", "issue_opened");
    }

    @Test
    void aPrivateRepoActivityFeedIsHiddenFromAnAnonymousCaller() {
        User owner = users.save(new User("hidden-activity-owner", "hidden-activity@cairn.dev", ""));
        repos.save(new Repo("hidden-activity-repo", owner, null, Visibility.PRIVATE));
        repositories.resolve("hidden-activity-owner", "hidden-activity-repo");

        var response = rest.getForEntity(
                baseUrl() + "/api/repos/hidden-activity-owner/hidden-activity-repo/activity", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
