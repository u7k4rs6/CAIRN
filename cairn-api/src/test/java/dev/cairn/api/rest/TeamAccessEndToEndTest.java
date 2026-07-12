package dev.cairn.api.rest;

import dev.cairn.api.auth.TokenHasher;
import dev.cairn.api.domain.PersonalAccessToken;
import dev.cairn.api.domain.User;
import dev.cairn.api.repo.PersonalAccessTokenJpaRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gap-closure round's P0 "done when": through the running app (REST API, no
 * database fixture shortcuts), an org admin creates an org, creates a team, grants
 * the team write on a repo, and a real member of that team can then push with a real
 * {@code git} binary. This is the exact end-to-end path the original build's SUMMARY
 * implied existed but didn't: {@code Organization}/{@code Team}/{@code TeamGrant}
 * were fully modeled and unit-tested against {@link dev.cairn.api.permission.DefaultPermissionResolver}
 * (see {@code DefaultPermissionResolverTest#teamGrantAppliesToADirectMember}), but
 * nothing let a real caller create any of those rows before {@link OrgController}
 * and {@link AccessController} existed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeamAccessEndToEndTest {

    @TempDir
    static Path reposDir;

    @DynamicPropertySource
    static void repoDir(DynamicPropertyRegistry registry) {
        registry.add("cairn.repos-dir", () -> reposDir.toString());
    }

    @LocalServerPort
    int port;

    @TempDir
    Path work;

    @Autowired
    UserJpaRepository users;

    @Autowired
    PersonalAccessTokenJpaRepository tokens;

    @Autowired
    TokenHasher tokenHasher;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private record Actor(String username, String rawToken) {
    }

    private Actor newActor(String username) {
        User user = users.save(new User(username, username + "@cairn.dev", ""));
        String rawToken = "token-" + UUID.randomUUID();
        tokens.save(new PersonalAccessToken(user, tokenHasher.hash(rawToken), "repo:write,org:admin", null));
        return new Actor(username, rawToken);
    }

    private HttpHeaders authHeaders(Actor actor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(actor.username(), actor.rawToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void post(String path, Actor actor, String body) {
        var response = rest.exchange(baseUrl() + path, HttpMethod.POST, new HttpEntity<>(body, authHeaders(actor)), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("POST " + path + " -> " + response.getStatusCode() + ": " + response.getBody())
                .isTrue();
    }

    private String cloneUrl(Actor actor, String owner, String repoPath) {
        return "http://" + actor.username() + ":" + actor.rawToken() + "@localhost:" + port + "/" + repoPath;
    }

    private record ProcessResult(int exitCode, String output) {
        void assertSuccess() {
            assertThat(exitCode).as("command output:\n" + output).isEqualTo(0);
        }
    }

    private ProcessResult run(Path dir, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
        builder.environment().put("GIT_AUTHOR_NAME", "Team Member");
        builder.environment().put("GIT_AUTHOR_EMAIL", "member@cairn.dev");
        builder.environment().put("GIT_COMMITTER_NAME", "Team Member");
        builder.environment().put("GIT_COMMITTER_EMAIL", "member@cairn.dev");
        Process process = builder.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        process.getInputStream().transferTo(out);
        int exit = process.waitFor();
        return new ProcessResult(exit, out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void anOrgAdminGrantsATeamWriteAndARealTeamMemberPushesWithGit() throws Exception {
        Actor admin = newActor("admin-" + UUID.randomUUID().toString().substring(0, 8));
        Actor engineer = newActor("engineer-" + UUID.randomUUID().toString().substring(0, 8));
        Actor outsider = newActor("outsider-" + UUID.randomUUID().toString().substring(0, 8));
        String orgName = "acme-" + UUID.randomUUID().toString().substring(0, 8);
        String repoName = "widget";

        // 1. The org admin creates the org, a team, and adds the engineer to it -
        //    entirely through the REST API, no direct database fixture.
        post("/api/orgs", admin, "{\"name\":\"" + orgName + "\"}");
        post("/api/orgs/" + orgName + "/teams", admin, "{\"name\":\"backend\"}");
        post("/api/orgs/" + orgName + "/teams/backend/members", admin, "{\"username\":\"" + engineer.username() + "\"}");

        // 2. The admin creates an org-owned private repo and grants the team WRITE.
        post("/api/repos", admin, "{\"name\":\"" + repoName + "\",\"visibility\":\"PRIVATE\",\"org\":\"" + orgName + "\"}");
        post("/api/repos/" + orgName + "/" + repoName + "/access/teams", admin,
                "{\"org\":\"" + orgName + "\",\"team\":\"backend\",\"role\":\"WRITE\"}");

        // 3. The engineer, who never received any grant of their own, pushes with a
        //    real git binary and it must succeed purely because of the team grant.
        Path origin = work.resolve("origin");
        Files.createDirectories(origin);
        run(origin, "git", "init", "-q", "-b", "main").assertSuccess();
        Files.writeString(origin.resolve("README.md"), "# widget\n");
        run(origin, "git", "add", "README.md").assertSuccess();
        run(origin, "git", "commit", "-q", "-m", "initial commit").assertSuccess();
        run(origin, "git", "remote", "add", "cairn", cloneUrl(engineer, orgName, orgName + "/" + repoName + ".git")).assertSuccess();

        ProcessResult push = run(origin, "git", "push", "cairn", "main");
        push.assertSuccess();

        // 4. An outsider with no grant at all, direct or via a team, is rejected.
        Path outsiderClone = work.resolve("outsider-attempt");
        Files.createDirectories(outsiderClone);
        ProcessResult clone = run(work, "git", "clone", cloneUrl(outsider, orgName, orgName + "/" + repoName + ".git"), outsiderClone.toString());
        assertThat(clone.exitCode()).isNotEqualTo(0);

        // 5. The engineer can also clone what they just pushed, proving read follows
        //    from the same team grant (WRITE implies READ, security doc section 3.2).
        Path engineerClone = work.resolve("engineer-clone");
        run(work, "git", "clone", cloneUrl(engineer, orgName, orgName + "/" + repoName + ".git"), engineerClone.toString()).assertSuccess();
        assertThat(Files.readString(engineerClone.resolve("README.md"))).isEqualTo("# widget\n");
    }
}
