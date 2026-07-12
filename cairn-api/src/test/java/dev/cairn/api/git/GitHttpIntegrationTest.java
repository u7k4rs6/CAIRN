package dev.cairn.api.git;

import dev.cairn.api.auth.TokenHasher;
import dev.cairn.api.domain.PersonalAccessToken;
import dev.cairn.api.domain.User;
import dev.cairn.api.repo.PersonalAccessTokenJpaRepository;
import dev.cairn.api.repo.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The PRD's M5 gate, driven with a real {@code git} binary against a real running
 * server: clone succeeds, push succeeds, and a fetch after new commits picks up the
 * new history. (The negotiation efficiency claim itself, that a fetch sends only the
 * new objects and not the whole repository, is proven precisely by
 * {@code UploadPackHandlerTest} in cairn-transfer, which can assert an exact object
 * count; this test proves the same protocol actually works end to end with the real
 * client, which a unit test alone cannot.)
 *
 * <p>Since M6, pushing requires write access (security doc, section 4.3), so this test
 * pre-provisions the repo's owning user and a personal access token and embeds it in
 * the push remote URL, the standard Git-over-HTTP credential pattern.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GitHttpIntegrationTest {

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

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Registers the owning user up front (mirroring what {@code RepoService} would auto-create) and mints it a token, for push URLs. */
    private String authenticatedUrl(String owner, String repoPath) {
        User user = users.findByUsername(owner).orElseGet(() -> users.save(new User(owner, owner + "@cairn.dev", "")));
        String rawToken = "test-token-" + UUID.randomUUID();
        tokens.save(new PersonalAccessToken(user, tokenHasher.hash(rawToken), "repo:write", null));
        return "http://" + owner + ":" + rawToken + "@localhost:" + port + "/" + repoPath;
    }

    private ProcessResult run(Path dir, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true);
        builder.environment().put("GIT_AUTHOR_NAME", "Ada");
        builder.environment().put("GIT_AUTHOR_EMAIL", "ada@cairn.dev");
        builder.environment().put("GIT_COMMITTER_NAME", "Ada");
        builder.environment().put("GIT_COMMITTER_EMAIL", "ada@cairn.dev");
        Process process = builder.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        process.getInputStream().transferTo(out);
        int exit = process.waitFor();
        String output = out.toString(StandardCharsets.UTF_8);
        return new ProcessResult(exit, output);
    }

    private record ProcessResult(int exitCode, String output) {
        void assertSuccess() {
            assertThat(exitCode).as("command output:\n" + output).isEqualTo(0);
        }
    }

    @Test
    void cloningAnEmptyRepositorySucceeds() throws Exception {
        Path cloneDir = work.resolve("empty-clone");
        Files.createDirectories(cloneDir);
        ProcessResult result = run(work, "git", "clone", baseUrl() + "/acme/empty-repo.git", cloneDir.toString());
        result.assertSuccess();
        assertThat(Files.isDirectory(cloneDir.resolve(".git"))).isTrue();
    }

    @Test
    void pushCloneAndFetchRoundTripThroughARealGitClient() throws Exception {
        Path origin = work.resolve("origin");
        Files.createDirectories(origin);
        run(origin, "git", "init", "-q", "-b", "main").assertSuccess();
        Files.writeString(origin.resolve("README.md"), "# Cairn demo\n");
        run(origin, "git", "add", "README.md").assertSuccess();
        run(origin, "git", "commit", "-q", "-m", "initial commit").assertSuccess();
        run(origin, "git", "remote", "add", "cairn", authenticatedUrl("acme", "acme/demo.git")).assertSuccess();

        run(origin, "git", "push", "cairn", "main").assertSuccess();

        Path clone = work.resolve("clone");
        run(work, "git", "clone", baseUrl() + "/acme/demo.git", clone.toString()).assertSuccess();
        assertThat(Files.readString(clone.resolve("README.md"))).isEqualTo("# Cairn demo\n");

        ProcessResult log1 = run(clone, "git", "log", "--oneline");
        assertThat(log1.output()).contains("initial commit");

        // A new commit on the origin, pushed, then fetched into the clone.
        Files.writeString(origin.resolve("SECOND.md"), "second file\n");
        run(origin, "git", "add", "SECOND.md").assertSuccess();
        run(origin, "git", "commit", "-q", "-m", "second commit").assertSuccess();
        run(origin, "git", "push", "cairn", "main").assertSuccess();

        run(clone, "git", "fetch", "origin", "main").assertSuccess();
        run(clone, "git", "merge", "origin/main", "-q", "-m", "merge").assertSuccess();

        assertThat(Files.exists(clone.resolve("SECOND.md"))).isTrue();
        ProcessResult log2 = run(clone, "git", "log", "--oneline");
        assertThat(log2.output()).contains("second commit");

        ProcessResult fsck = run(clone, "git", "fsck", "--full");
        assertThat(fsck.exitCode()).as("fsck output:\n" + fsck.output()).isEqualTo(0);
    }

    @Test
    void anonymousPushIsRejectedWithAnAuthChallenge() throws Exception {
        // No credentials at all: the server must challenge (401), not silently
        // accept or leak an in-band error before the client even has a chance to
        // authenticate. With no embedded credentials to retry with, git simply fails.
        Path origin = work.resolve("origin-noauth");
        Files.createDirectories(origin);
        run(origin, "git", "init", "-q", "-b", "main").assertSuccess();
        Files.writeString(origin.resolve("f.txt"), "content\n");
        run(origin, "git", "add", "f.txt").assertSuccess();
        run(origin, "git", "commit", "-q", "-m", "commit").assertSuccess();
        run(origin, "git", "remote", "add", "cairn", baseUrl() + "/acme/no-write.git").assertSuccess();

        ProcessResult push = run(origin, "git", "push", "cairn", "main");

        assertThat(push.exitCode()).isNotEqualTo(0);
    }

    @Test
    void privateRepoIsUnreadableWithoutAGrant() throws Exception {
        // PRD acceptance criterion, verified against the real HTTP boundary (not
        // just DefaultPermissionResolverTest's unit-level coverage): a private repo
        // created via the REST API cannot be cloned anonymously, but can be cloned
        // by its owner using a valid token.
        User owner = users.save(new User("secretive", "secretive@cairn.dev", ""));
        String rawToken = "owner-token-" + UUID.randomUUID();
        tokens.save(new PersonalAccessToken(owner, tokenHasher.hash(rawToken), "repo:read", null));

        var restTemplate = new org.springframework.web.client.RestTemplate();
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBasicAuth("secretive", rawToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var body = "{\"name\":\"vault\",\"visibility\":\"PRIVATE\"}";
        var createResponse = restTemplate.exchange(baseUrl() + "/api/repos", org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(body, headers), String.class);
        assertThat(createResponse.getStatusCode().is2xxSuccessful()).isTrue();

        Path anonClone = work.resolve("anon-clone");
        Files.createDirectories(anonClone);
        ProcessResult anonAttempt = run(work, "git", "clone", baseUrl() + "/secretive/vault.git", anonClone.toString());
        assertThat(anonAttempt.exitCode()).isNotEqualTo(0);

        Path ownerClone = work.resolve("owner-clone");
        String ownerUrl = "http://secretive:" + rawToken + "@localhost:" + port + "/secretive/vault.git";
        run(work, "git", "clone", ownerUrl, ownerClone.toString()).assertSuccess();
    }
}
