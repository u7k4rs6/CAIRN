package dev.cairn.api.rest;

import dev.cairn.api.auth.TokenHasher;
import dev.cairn.api.domain.PersonalAccessToken;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.repo.PersonalAccessTokenJpaRepository;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.api.repo.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** PRD Tier 2: labels, milestones, and assignees on issues, plus FilterBar-style filtering (frontend spec, section 5.7). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IssueControllerTest {

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

    private final TestRestTemplate rest = new TestRestTemplate();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private record Actor(String username, String rawToken) {
    }

    private Actor newActor(String prefix) {
        // A UUID suffix, not just the per-class counter: the whole test suite shares
        // one H2 in-memory database across every @SpringBootTest class in the same
        // JVM (application.yml's jdbc:h2:mem:cairn is not per-class), so a small
        // sequential counter alone collides with an identically-prefixed actor from
        // a different test class (this exact bug: IssueControllerTest's "owner1"
        // collided with OrgAndAccessControllerTest's "owner1").
        int n = COUNTER.incrementAndGet();
        String username = prefix + n + "-" + UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User(username, username + "@cairn.dev", ""));
        String rawToken = "token-" + UUID.randomUUID();
        tokens.save(new PersonalAccessToken(user, tokenHasher.hash(rawToken), "repo:write", null));
        return new Actor(user.username(), rawToken);
    }

    private HttpHeaders headers(Actor actor) {
        HttpHeaders h = new HttpHeaders();
        h.setBasicAuth(actor.username(), actor.rawToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<Map> post(String path, Actor actor, String body) {
        return rest.exchange(baseUrl() + path, HttpMethod.POST, new HttpEntity<>(body, headers(actor)), Map.class);
    }

    private ResponseEntity<List> getList(String path, Actor actor) {
        return rest.exchange(baseUrl() + path, HttpMethod.GET, new HttpEntity<>(headers(actor)), List.class);
    }

    private String seedRepo(Actor owner) {
        String repoName = "proj-" + COUNTER.incrementAndGet();
        post("/api/repos", owner, "{\"name\":\"" + repoName + "\",\"visibility\":\"PUBLIC\"}");
        return owner.username() + "/" + repoName;
    }

    @Test
    void aTriageUserCanCreateALabelAndApplyItToAnIssue() {
        Actor owner = newActor("owner");
        String path = seedRepo(owner);

        var label = post("/api/repos/" + path + "/labels", owner, "{\"name\":\"bug\",\"color\":\"d73a4a\"}");
        assertThat(label.getStatusCode().is2xxSuccessful()).isTrue();
        Long labelId = ((Number) label.getBody().get("id")).longValue();

        var issue = post("/api/repos/" + path + "/issues", owner, "{\"title\":\"crash on startup\",\"body\":\"...\"}");
        Long issueId = ((Number) issue.getBody().get("id")).longValue();

        var applied = post("/api/repos/" + path + "/issues/" + issueId + "/labels", owner,
                "{\"labelId\":" + labelId + "}");
        assertThat(applied.getStatusCode().is2xxSuccessful()).isTrue();

        var filtered = getList("/api/repos/" + path + "/issues?label=bug", owner);
        assertThat(filtered.getBody()).hasSize(1);

        var notFiltered = getList("/api/repos/" + path + "/issues?label=nonexistent", owner);
        assertThat(notFiltered.getBody()).isEmpty();
    }

    @Test
    void addingALabelFromAnotherRepoIsRejected() {
        Actor owner = newActor("owner");
        String path = seedRepo(owner);
        String otherPath = seedRepo(owner);

        var foreignLabel = post("/api/repos/" + otherPath + "/labels", owner, "{\"name\":\"bug\",\"color\":\"d73a4a\"}");
        Long foreignLabelId = ((Number) foreignLabel.getBody().get("id")).longValue();

        var issue = post("/api/repos/" + path + "/issues", owner, "{\"title\":\"x\",\"body\":\"y\"}");
        Long issueId = ((Number) issue.getBody().get("id")).longValue();

        var applied = post("/api/repos/" + path + "/issues/" + issueId + "/labels", owner,
                "{\"labelId\":" + foreignLabelId + "}");
        assertThat(applied.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void anIssueCanBeAssignedAndFilteredByAssignee() {
        Actor owner = newActor("owner");
        Actor assignee = newActor("dev");
        String path = seedRepo(owner);

        var issue = post("/api/repos/" + path + "/issues", owner, "{\"title\":\"x\",\"body\":\"y\"}");
        Long issueId = ((Number) issue.getBody().get("id")).longValue();

        var assigned = post("/api/repos/" + path + "/issues/" + issueId + "/assignees", owner,
                "{\"username\":\"" + assignee.username() + "\"}");
        assertThat(assigned.getStatusCode().is2xxSuccessful()).isTrue();

        var filtered = getList("/api/repos/" + path + "/issues?assignee=" + assignee.username(), owner);
        assertThat(filtered.getBody()).hasSize(1);

        var revoke = rest.exchange(baseUrl() + "/api/repos/" + path + "/issues/" + issueId + "/assignees/" + assignee.username(),
                HttpMethod.DELETE, new HttpEntity<>(headers(owner)), Void.class);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var afterRevoke = getList("/api/repos/" + path + "/issues?assignee=" + assignee.username(), owner);
        assertThat(afterRevoke.getBody()).isEmpty();
    }

    @Test
    void anIssueCanBeAssignedToAMilestoneAndFilteredByIt() {
        Actor owner = newActor("owner");
        String path = seedRepo(owner);

        var milestone = post("/api/repos/" + path + "/milestones", owner, "{\"title\":\"v1.0\"}");
        assertThat(milestone.getStatusCode().is2xxSuccessful()).isTrue();
        Long milestoneId = ((Number) milestone.getBody().get("id")).longValue();

        var issue = post("/api/repos/" + path + "/issues", owner, "{\"title\":\"x\",\"body\":\"y\"}");
        Long issueId = ((Number) issue.getBody().get("id")).longValue();

        var set = rest.exchange(baseUrl() + "/api/repos/" + path + "/issues/" + issueId + "/milestone",
                HttpMethod.PUT, new HttpEntity<>("{\"milestoneId\":" + milestoneId + "}", headers(owner)), Map.class);
        assertThat(set.getStatusCode().is2xxSuccessful()).isTrue();

        var filtered = getList("/api/repos/" + path + "/issues?milestone=" + milestoneId, owner);
        assertThat(filtered.getBody()).hasSize(1);
    }

    @Test
    void aMilestoneCanBeClosedAndReopened() {
        Actor owner = newActor("owner");
        String path = seedRepo(owner);
        var milestone = post("/api/repos/" + path + "/milestones", owner, "{\"title\":\"v1.0\"}");
        Long milestoneId = ((Number) milestone.getBody().get("id")).longValue();

        var closed = rest.exchange(baseUrl() + "/api/repos/" + path + "/milestones/" + milestoneId + "/state",
                HttpMethod.PUT, new HttpEntity<>("\"CLOSED\"", headers(owner)), Map.class);
        assertThat(closed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(closed.getBody().get("state")).isEqualTo("CLOSED");

        var reopened = rest.exchange(baseUrl() + "/api/repos/" + path + "/milestones/" + milestoneId + "/state",
                HttpMethod.PUT, new HttpEntity<>("\"OPEN\"", headers(owner)), Map.class);
        assertThat(reopened.getBody().get("state")).isEqualTo("OPEN");
    }

    @Test
    void aReaderWithoutTriageCannotCreateALabel() {
        Actor owner = newActor("owner");
        String path = seedRepo(owner);
        Actor stranger = newActor("stranger");

        var denied = post("/api/repos/" + path + "/labels", stranger, "{\"name\":\"bug\",\"color\":\"d73a4a\"}");
        assertThat(denied.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void filteringByOpenStateExcludesClosedIssues() {
        Actor owner = newActor("owner");
        String path = seedRepo(owner);
        post("/api/repos/" + path + "/issues", owner, "{\"title\":\"open one\",\"body\":\"y\"}");

        var openOnly = getList("/api/repos/" + path + "/issues?state=OPEN", owner);
        assertThat(openOnly.getBody()).hasSize(1);

        var closedOnly = getList("/api/repos/" + path + "/issues?state=CLOSED", owner);
        assertThat(closedOnly.getBody()).isEmpty();
    }
}
