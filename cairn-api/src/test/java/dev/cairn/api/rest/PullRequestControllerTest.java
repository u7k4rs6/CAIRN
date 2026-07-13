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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** M8's own DECISIONS.md named the missing single-resource {@code GET .../pulls/{number}} endpoint as a gap; this proves it now works. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PullRequestControllerTest {

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
    PersonalAccessTokenJpaRepository tokens;

    @Autowired
    TokenHasher tokenHasher;

    @Autowired
    RepositoryRegistry repositories;

    private final TestRestTemplate rest = new TestRestTemplate();
    private static final PersonIdent PERSON = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders(String username, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void aSingleResourceEndpointReturnsOnePullRequestByNumberAndMasksAMissingOne() {
        int n = COUNTER.incrementAndGet();
        String owner = "pr-owner-" + n;
        String repoName = "pr-demo-" + n;
        User user = users.save(new User(owner, owner + "@cairn.dev", ""));
        repos.save(new Repo(repoName, user, null, Visibility.PUBLIC));
        String rawToken = "token-" + UUID.randomUUID();
        tokens.save(new PersonalAccessToken(user, tokenHasher.hash(rawToken), "repo:write", null));

        var handle = repositories.resolve(owner, repoName);
        var store = handle.objectStore();
        ObjectId baseTree = store.put(new Tree(List.of()));
        ObjectId root = store.put(new Commit(baseTree, List.of(), PERSON, PERSON, "root"));
        GenerationNumbers.computeAndStore(store, handle.generations(), root);
        handle.refStore().update("refs/heads/main", root);

        ObjectId blob = store.put(new Blob("feature\n".getBytes()));
        ObjectId featureTree = store.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "f.txt", blob))));
        ObjectId featureCommit = store.put(new Commit(featureTree, List.of(root), PERSON, PERSON, "feature"));
        GenerationNumbers.computeAndStore(store, handle.generations(), featureCommit);
        handle.refStore().update("refs/heads/feature", featureCommit);

        var created = rest.exchange(baseUrl() + "/api/repos/" + owner + "/" + repoName + "/pulls", HttpMethod.POST,
                new HttpEntity<>("{\"title\":\"add feature\",\"sourceRef\":\"refs/heads/feature\",\"targetRef\":\"refs/heads/main\"}",
                        authHeaders(owner, rawToken)),
                Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        long number = ((Number) created.getBody().get("id")).longValue();

        var single = rest.getForEntity(baseUrl() + "/api/repos/" + owner + "/" + repoName + "/pulls/" + number, Map.class);
        assertThat(single.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(single.getBody().get("title")).isEqualTo("add feature");

        var missing = rest.getForEntity(baseUrl() + "/api/repos/" + owner + "/" + repoName + "/pulls/999999", String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
    }
}
