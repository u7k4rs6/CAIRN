package dev.cairn.api.rest;

import dev.cairn.api.auth.PasswordHasher;
import dev.cairn.api.auth.TokenGenerator;
import dev.cairn.api.auth.TokenHasher;
import dev.cairn.api.domain.PersonalAccessToken;
import dev.cairn.api.domain.User;
import dev.cairn.api.repo.PersonalAccessTokenJpaRepository;
import dev.cairn.api.repo.UserJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Account creation and token minting: before this controller, {@link PasswordHasher}
 * (BCrypt, per security doc section 2.2 - see PasswordHasher's own Javadoc for why
 * BCrypt rather than the doc's original argon2id) existed and was unit-testable
 * but was called from nowhere in production code, because there was no endpoint that
 * created a {@link User} at all outside {@code DevDataSeeder}'s one hardcoded demo
 * account. Every other gap-closure fix in this round (orgs, teams, grants) still
 * assumed a {@code User} row already existed; this is the actual root of the
 * reachability chain, so a real visitor can become a real account.
 *
 * <p>There is no session/cookie login yet (that is the P2 gap-closure item on
 * replacing localStorage-PAT auth with real sessions, security doc section 2.2's
 * full form). Minting the first token therefore authenticates with the real
 * password directly over Basic auth, once, specifically for this endpoint; every
 * other endpoint in the app continues to authenticate via {@link dev.cairn.api.auth.PrincipalResolver}'s
 * token-only lookup, per FR/security doc section 2.3.
 */
@RestController
public class AccountController {

    private final UserJpaRepository users;
    private final PersonalAccessTokenJpaRepository tokens;
    private final PasswordHasher passwordHasher;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;

    public AccountController(UserJpaRepository users, PersonalAccessTokenJpaRepository tokens,
                              PasswordHasher passwordHasher, TokenGenerator tokenGenerator, TokenHasher tokenHasher) {
        this.users = users;
        this.tokens = tokens;
        this.passwordHasher = passwordHasher;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
    }

    public record SignupRequest(String username, String email, String password) {
    }

    public record UserView(String username, String email) {
    }

    public record MintTokenRequest(String scopes, Long expiresInDays) {
    }

    public record MintedTokenView(String token, String scopes) {
    }

    @PostMapping("/api/users")
    public ResponseEntity<?> signup(@RequestBody SignupRequest body) {
        if (body.username() == null || body.username().isBlank() || body.password() == null || body.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and password are required"));
        }
        if (users.findByUsername(body.username()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "username '" + body.username() + "' is already taken"));
        }
        User user = users.save(new User(body.username(), body.email(), passwordHasher.hash(body.password())));
        return ResponseEntity.ok(new UserView(user.username(), user.email()));
    }

    /** Decodes {@code Authorization: Basic <username>:<password>} without going through {@link dev.cairn.api.auth.PrincipalResolver}, which only ever looks up a token, never a real password. */
    private Optional<String[]> decodeBasicAuth(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Optional.empty();
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) {
                return Optional.empty();
            }
            return Optional.of(new String[] {decoded.substring(0, colon), decoded.substring(colon + 1)});
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @PostMapping("/api/users/{username}/tokens")
    public ResponseEntity<?> mintToken(@PathVariable String username, @RequestBody MintTokenRequest body, HttpServletRequest request) {
        Optional<String[]> credentials = decodeBasicAuth(request);
        if (credentials.isEmpty()) {
            return ResponseEntity.status(401).header("WWW-Authenticate", "Basic realm=\"cairn\"").build();
        }
        String basicUsername = credentials.get()[0];
        String password = credentials.get()[1];
        if (!basicUsername.equals(username)) {
            return ResponseEntity.status(403).body(Map.of("error", "can only mint a token for your own account"));
        }
        User user = users.findByUsername(username).orElse(null);
        if (user == null || !passwordHasher.matches(password, user.passwordHash())) {
            return ResponseEntity.status(401).header("WWW-Authenticate", "Basic realm=\"cairn\"").build();
        }
        String scopes = (body.scopes() == null || body.scopes().isBlank()) ? "repo:read,repo:write,org:admin" : body.scopes();
        Instant expiresAt = body.expiresInDays() == null ? null : Instant.now().plus(body.expiresInDays(), ChronoUnit.DAYS);
        String rawToken = tokenGenerator.generate();
        tokens.save(new PersonalAccessToken(user, tokenHasher.hash(rawToken), scopes, expiresAt));
        return ResponseEntity.ok(new MintedTokenView(rawToken, scopes));
    }
}
