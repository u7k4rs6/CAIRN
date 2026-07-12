package dev.cairn.api.rest;

import dev.cairn.api.auth.TokenHasher;
import dev.cairn.api.domain.Organization;
import dev.cairn.api.domain.PersonalAccessToken;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.repo.OrganizationJpaRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gap-closure round's P0: {@link OrgController} and {@link AccessController}
 * gave the org/team/grant domain model (fully built and unit-tested in M6, but
 * unreachable by any real user action) an actual REST surface. These tests drive it
 * through a real HTTP boundary, the same discipline as {@code RepoContentControllerTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrgAndAccessControllerTest {

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
    OrganizationJpaRepository organizations;

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

    private record Actor(User user, String rawToken) {
    }

    private Actor newActor(String usernamePrefix) {
        int n = COUNTER.incrementAndGet();
        User user = users.save(new User(usernamePrefix + n, usernamePrefix + n + "@cairn.dev", ""));
        String rawToken = "token-" + UUID.randomUUID();
        tokens.save(new PersonalAccessToken(user, tokenHasher.hash(rawToken), "repo:write,org:admin", null));
        return new Actor(user, rawToken);
    }

    private HttpHeaders authHeaders(Actor actor) {
        HttpHeaders headers = new HttpHeaders();
        if (actor != null) {
            headers.setBasicAuth(actor.user().username(), actor.rawToken());
        }
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<Map> post(String path, Actor actor, String body) {
        return rest.exchange(baseUrl() + path, HttpMethod.POST, new HttpEntity<>(body, authHeaders(actor)), Map.class);
    }

    private ResponseEntity<Map> put(String path, Actor actor, String body) {
        return rest.exchange(baseUrl() + path, HttpMethod.PUT, new HttpEntity<>(body, authHeaders(actor)), Map.class);
    }

    private ResponseEntity<Map> get(String path, Actor actor) {
        return rest.exchange(baseUrl() + path, HttpMethod.GET, new HttpEntity<>(authHeaders(actor)), Map.class);
    }

    private ResponseEntity<List> getList(String path, Actor actor) {
        return rest.exchange(baseUrl() + path, HttpMethod.GET, new HttpEntity<>(authHeaders(actor)), List.class);
    }

    private ResponseEntity<Void> delete(String path, Actor actor) {
        return rest.exchange(baseUrl() + path, HttpMethod.DELETE, new HttpEntity<>(authHeaders(actor)), Void.class);
    }

    @Test
    void aUserCanCreateAnOrgAndBecomesItsAdmin() {
        Actor founder = newActor("founder");
        var response = post("/api/orgs", founder, "{\"name\":\"acme-" + COUNTER.get() + "\"}");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().get("owner")).isEqualTo(founder.user().username());
    }

    @Test
    void anonymousCannotCreateAnOrg() {
        var response = post("/api/orgs", null, "{\"name\":\"anon-org\"}");
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void onlyTheOrgAdminCanCreateATeamOnIt() {
        Actor founder = newActor("owner");
        String orgName = "org-" + COUNTER.get();
        post("/api/orgs", founder, "{\"name\":\"" + orgName + "\"}");

        Actor stranger = newActor("stranger");
        var denied = post("/api/orgs/" + orgName + "/teams", stranger, "{\"name\":\"backend\"}");
        assertThat(denied.getStatusCode().value()).isEqualTo(403);

        var allowed = post("/api/orgs/" + orgName + "/teams", founder, "{\"name\":\"backend\"}");
        assertThat(allowed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(allowed.getBody().get("name")).isEqualTo("backend");
    }

    @Test
    void teamsCanNestUnderAParentAndAcceptMembers() {
        Actor founder = newActor("owner");
        String orgName = "org-" + COUNTER.get();
        post("/api/orgs", founder, "{\"name\":\"" + orgName + "\"}");
        post("/api/orgs/" + orgName + "/teams", founder, "{\"name\":\"engineering\"}");
        var child = post("/api/orgs/" + orgName + "/teams", founder, "{\"name\":\"backend\",\"parentTeam\":\"engineering\"}");
        assertThat(child.getBody().get("parentTeam")).isEqualTo("engineering");

        Actor engineer = newActor("engineer");
        var added = post("/api/orgs/" + orgName + "/teams/backend/members", founder,
                "{\"username\":\"" + engineer.user().username() + "\"}");
        assertThat(added.getStatusCode().is2xxSuccessful()).isTrue();

        var members = getList("/api/orgs/" + orgName + "/teams/backend/members", founder);
        assertThat(members.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void repoAdminCanGrantAndRevokeACollaboratorRoleAndCannotRemoveTheOwner() {
        Actor owner = newActor("repo-owner");
        String repoName = "priv-" + COUNTER.get();
        post("/api/repos", owner, "{\"name\":\"" + repoName + "\",\"visibility\":\"PRIVATE\"}");
        Actor collaborator = newActor("collab");

        var grant = post("/api/repos/" + owner.user().username() + "/" + repoName + "/access/collaborators", owner,
                "{\"username\":\"" + collaborator.user().username() + "\",\"role\":\"WRITE\"}");
        assertThat(grant.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(grant.getBody().get("role")).isEqualTo("WRITE");

        var access = get("/api/repos/" + owner.user().username() + "/" + repoName + "/access", owner);
        assertThat(access.getBody().get("collaborators").toString()).contains(collaborator.user().username());

        var ownerRemoval = rest.exchange(
                baseUrl() + "/api/repos/" + owner.user().username() + "/" + repoName + "/access/collaborators/" + owner.user().username(),
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(owner)), Map.class);
        assertThat(ownerRemoval.getStatusCode().value()).isEqualTo(400);

        var revoke = delete("/api/repos/" + owner.user().username() + "/" + repoName + "/access/collaborators/" + collaborator.user().username(), owner);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void accessManagementIsMaskedAsNotFoundForANonAdmin() {
        Actor owner = newActor("owner");
        String repoName = "secret-" + COUNTER.get();
        post("/api/repos", owner, "{\"name\":\"" + repoName + "\",\"visibility\":\"PRIVATE\"}");

        Actor stranger = newActor("stranger");
        var response = get("/api/repos/" + owner.user().username() + "/" + repoName + "/access", stranger);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void repoAdminCanGrantATeamRoleOnARepo() {
        Actor owner = newActor("owner");
        String orgName = "org-" + COUNTER.get();
        post("/api/orgs", owner, "{\"name\":\"" + orgName + "\"}");
        post("/api/orgs/" + orgName + "/teams", owner, "{\"name\":\"backend\"}");

        String repoName = "teamrepo-" + COUNTER.get();
        post("/api/repos", owner, "{\"name\":\"" + repoName + "\",\"visibility\":\"PRIVATE\"}");

        var grant = post("/api/repos/" + owner.user().username() + "/" + repoName + "/access/teams", owner,
                "{\"org\":\"" + orgName + "\",\"team\":\"backend\",\"role\":\"WRITE\"}");
        assertThat(grant.getStatusCode().is2xxSuccessful()).isTrue();

        var access = get("/api/repos/" + owner.user().username() + "/" + repoName + "/access", owner);
        assertThat(access.getBody().get("teamGrants").toString()).contains("backend");

        var revoke = delete("/api/repos/" + owner.user().username() + "/" + repoName + "/access/teams/" + orgName + "/backend", owner);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void repoAdminCanChangeVisibility() {
        Actor owner = newActor("owner");
        String repoName = "vis-" + COUNTER.get();
        post("/api/repos", owner, "{\"name\":\"" + repoName + "\",\"visibility\":\"PRIVATE\"}");

        var patched = put("/api/repos/" + owner.user().username() + "/" + repoName + "/visibility", owner,
                "{\"visibility\":\"PUBLIC\"}");
        assertThat(patched.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(patched.getBody().get("visibility")).isEqualTo("PUBLIC");

        Repo reloaded = repos.findByOwnerAndName(owner.user().username(), repoName).orElseThrow();
        assertThat(reloaded.visibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void repoAdminCanSetAndRemoveBranchProtection() {
        Actor owner = newActor("owner");
        String repoName = "prot-" + COUNTER.get();
        post("/api/repos", owner, "{\"name\":\"" + repoName + "\",\"visibility\":\"PUBLIC\"}");

        var set = rest.exchange(baseUrl() + "/api/repos/" + owner.user().username() + "/" + repoName + "/branch-protection/main",
                HttpMethod.PUT, new HttpEntity<>("{\"preventForcePush\":true,\"preventDeletion\":true,"
                        + "\"requireApprovalBeforeMerge\":false,\"minimumPushRole\":\"WRITE\"}", authHeaders(owner)), Map.class);
        assertThat(set.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(set.getBody().get("preventForcePush")).isEqualTo(true);

        var list = getList("/api/repos/" + owner.user().username() + "/" + repoName + "/branch-protection", owner);
        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();

        var removed = delete("/api/repos/" + owner.user().username() + "/" + repoName + "/branch-protection/main", owner);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void anOrgAdminCanCreateAnOrgOwnedRepoButAStrangerCannot() {
        Actor founder = newActor("orgowner");
        String orgName = "org-" + COUNTER.get();
        post("/api/orgs", founder, "{\"name\":\"" + orgName + "\"}");

        String repoName = "orgrepo-" + COUNTER.get();
        var created = post("/api/repos", founder, "{\"name\":\"" + repoName + "\",\"visibility\":\"PRIVATE\",\"org\":\"" + orgName + "\"}");
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(created.getBody().get("owner")).isEqualTo(orgName);

        Actor stranger = newActor("stranger");
        var denied = post("/api/repos", stranger, "{\"name\":\"other-" + COUNTER.get() + "\",\"visibility\":\"PRIVATE\",\"org\":\"" + orgName + "\"}");
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
    }
}
