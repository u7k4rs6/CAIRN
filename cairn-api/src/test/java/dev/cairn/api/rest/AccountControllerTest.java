package dev.cairn.api.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gap-closure round's most foundational P0: before {@link AccountController},
 * {@link dev.cairn.api.auth.PasswordHasher} existed (BCrypt, unit-tested) but was
 * wired into zero endpoints, so the only account that could ever exist was the one
 * hardcoded in {@code DevDataSeeder}. These tests drive real signup and real
 * token-minting through the HTTP boundary, then prove the minted token actually
 * authenticates a normal API call (repo creation), closing the loop end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountControllerTest {

    @TempDir
    static Path reposDir;

    @DynamicPropertySource
    static void repoDir(DynamicPropertyRegistry registry) {
        registry.add("cairn.repos-dir", () -> reposDir.toString());
    }

    @LocalServerPort
    int port;

    private final TestRestTemplate rest = new TestRestTemplate();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private ResponseEntity<Map> postJson(String path, String body, HttpHeaders extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (extraHeaders != null) {
            headers.addAll(extraHeaders);
        }
        return rest.exchange(baseUrl() + path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    void aNewUserCanSignUpAndIsRejectedOnADuplicateUsername() {
        String username = "newdev-" + COUNTER.incrementAndGet();
        var signup = postJson("/api/users", "{\"username\":\"" + username + "\",\"email\":\"" + username + "@cairn.dev\",\"password\":\"correct horse battery staple\"}", null);
        assertThat(signup.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(signup.getBody().get("username")).isEqualTo(username);

        var duplicate = postJson("/api/users", "{\"username\":\"" + username + "\",\"email\":\"x@cairn.dev\",\"password\":\"whatever\"}", null);
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void signupRequiresAUsernameAndPassword() {
        var missingPassword = postJson("/api/users", "{\"username\":\"nodev-" + COUNTER.incrementAndGet() + "\"}", null);
        assertThat(missingPassword.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void aNewUserCanMintATokenWithTheirRealPasswordAndUseItToAuthenticate() {
        String username = "founder-" + COUNTER.incrementAndGet();
        String password = "a genuinely long passphrase";
        postJson("/api/users", "{\"username\":\"" + username + "\",\"email\":\"" + username + "@cairn.dev\",\"password\":\"" + password + "\"}", null);

        HttpHeaders basic = new HttpHeaders();
        basic.setBasicAuth(username, password);
        var minted = postJson("/api/users/" + username + "/tokens", "{}", basic);
        assertThat(minted.getStatusCode().is2xxSuccessful()).isTrue();
        String token = (String) minted.getBody().get("token");
        assertThat(token).isNotBlank();

        // The minted token must actually authenticate a real API call: create a repo as this brand-new user.
        HttpHeaders tokenAuth = new HttpHeaders();
        tokenAuth.setBasicAuth(username, token);
        var repo = postJson("/api/repos", "{\"name\":\"first-repo\",\"visibility\":\"PRIVATE\"}", tokenAuth);
        assertThat(repo.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(repo.getBody().get("owner")).isEqualTo(username);
    }

    @Test
    void mintingATokenWithTheWrongPasswordIsRejected() {
        String username = "victim-" + COUNTER.incrementAndGet();
        postJson("/api/users", "{\"username\":\"" + username + "\",\"email\":\"v@cairn.dev\",\"password\":\"the-real-password\"}", null);

        HttpHeaders wrong = new HttpHeaders();
        wrong.setBasicAuth(username, "guessed-password");
        var minted = postJson("/api/users/" + username + "/tokens", "{}", wrong);
        assertThat(minted.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void aUserCannotMintATokenForSomeoneElsesAccount() {
        String victim = "victim2-" + COUNTER.incrementAndGet();
        postJson("/api/users", "{\"username\":\"" + victim + "\",\"email\":\"v@cairn.dev\",\"password\":\"victim-password\"}", null);
        String attacker = "attacker-" + COUNTER.incrementAndGet();
        postJson("/api/users", "{\"username\":\"" + attacker + "\",\"email\":\"a@cairn.dev\",\"password\":\"attacker-password\"}", null);

        HttpHeaders asAttacker = new HttpHeaders();
        asAttacker.setBasicAuth(attacker, "attacker-password");
        var minted = postJson("/api/users/" + victim + "/tokens", "{}", asAttacker);
        assertThat(minted.getStatusCode().value()).isEqualTo(403);
    }
}
