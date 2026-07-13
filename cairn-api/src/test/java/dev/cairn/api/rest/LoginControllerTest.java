package dev.cairn.api.rest;

import dev.cairn.api.auth.PasswordHasher;
import dev.cairn.api.domain.User;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 gap-closure: real server-side sessions replacing the localStorage-PAT-only
 * model, plus the CSRF check that cookie-based auth requires (security doc,
 * section 2.2). Login/logout/me, and the double-submit CSRF pattern on a real
 * mutating endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginControllerTest {

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
    PasswordHasher passwordHasher;

    private final TestRestTemplate rest = new TestRestTemplate();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private record LoggedIn(String cookieHeader, String csrfToken) {
    }

    private String newUser(String password) {
        int n = COUNTER.incrementAndGet();
        String username = "sess-user-" + n;
        users.save(new User(username, username + "@cairn.dev", passwordHasher.hash(password)));
        return username;
    }

    private String cookieHeaderFrom(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        return setCookies.stream().map(sc -> sc.split(";", 2)[0]).collect(Collectors.joining("; "));
    }

    private LoggedIn login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = rest.exchange(baseUrl() + "/api/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String cookieHeader = cookieHeaderFrom(response);
        String csrfToken = cookieHeader.split("cairn_csrf=")[1].split(";")[0];
        return new LoggedIn(cookieHeader, csrfToken);
    }

    @Test
    void loginWithTheWrongPasswordIsRejected() {
        String username = newUser("correct-password");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = rest.exchange(baseUrl() + "/api/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"wrong\"}", headers), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void meIsUnauthenticatedWithNoCookieAndAuthenticatedAfterLogin() {
        var anon = rest.getForEntity(baseUrl() + "/api/me", String.class);
        assertThat(anon.getStatusCode().value()).isEqualTo(401);

        String username = newUser("a-real-password");
        LoggedIn session = login(username, "a-real-password");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.cookieHeader());
        var me = rest.exchange(baseUrl() + "/api/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(me.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(me.getBody().get("username")).isEqualTo(username);
    }

    @Test
    void aMutationWithASessionCookieButNoCsrfHeaderIsRejected() {
        String username = newUser("pw12345");
        LoggedIn session = login(username, "pw12345");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.cookieHeader());
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = rest.exchange(baseUrl() + "/api/repos", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"no-csrf-repo\",\"visibility\":\"PUBLIC\"}", headers), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void aMutationWithASessionCookieAndTheMatchingCsrfHeaderSucceeds() {
        String username = newUser("pw12345");
        LoggedIn session = login(username, "pw12345");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.cookieHeader());
        headers.add("X-CSRF-Token", session.csrfToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = rest.exchange(baseUrl() + "/api/repos", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"with-csrf-repo\",\"visibility\":\"PUBLIC\"}", headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().get("owner")).isEqualTo(username);
    }

    @Test
    void logoutInvalidatesTheSession() {
        String username = newUser("pw12345");
        LoggedIn session = login(username, "pw12345");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.cookieHeader());
        headers.add("X-CSRF-Token", session.csrfToken());
        var logout = rest.exchange(baseUrl() + "/api/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var me = rest.exchange(baseUrl() + "/api/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(me.getStatusCode().value()).isEqualTo(401);
    }
}
