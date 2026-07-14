package dev.cairn.api.rest;

import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.api.repo.UserJpaRepository;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RepoControllerTest {

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

    private final TestRestTemplate rest = new TestRestTemplate();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void listingAnOwnersReposReturnsOnlyWhatAnAnonymousCallerMaySee() {
        int n = COUNTER.incrementAndGet();
        String ownerName = "list-owner-" + n;
        User owner = users.save(new User(ownerName, ownerName + "@cairn.dev", ""));
        repos.save(new Repo("public-one", owner, null, Visibility.PUBLIC));
        repos.save(new Repo("private-one", owner, null, Visibility.PRIVATE));

        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName, List.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(1);
        Map<?, ?> onlyRepo = (Map<?, ?>) response.getBody().get(0);
        assertThat(onlyRepo.get("name")).isEqualTo("public-one");
    }

    @Test
    void anOwnerWithNoVisibleReposReturnsAnEmptyListNotAnError() {
        int n = COUNTER.incrementAndGet();
        String ownerName = "empty-list-owner-" + n;
        User owner = users.save(new User(ownerName, ownerName + "@cairn.dev", ""));
        repos.save(new Repo("hidden", owner, null, Visibility.PRIVATE));

        var response = rest.getForEntity(baseUrl() + "/api/repos/" + ownerName, List.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void aNonexistentOwnerAlsoReturnsAnEmptyListRatherThanA404() {
        // An owner name is not itself a secret the way a specific repo's existence
        // can be (frontend spec, section 8's masking rule is about repos, not
        // owners) - GET /api/orgs/{org} is already a public, ungated read for the
        // same reason.
        var response = rest.getForEntity(baseUrl() + "/api/repos/no-such-owner-at-all", List.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEmpty();
    }
}
