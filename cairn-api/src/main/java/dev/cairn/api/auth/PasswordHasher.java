package dev.cairn.api.auth;

import com.password4j.BadParametersException;
import com.password4j.BcryptFunction;
import com.password4j.HashingFunction;
import com.password4j.Password;
import com.password4j.types.Bcrypt;
import org.springframework.stereotype.Component;

/**
 * BCrypt (security doc, section 2.2), explicitly configured rather than left to
 * password4j's auto-detected defaults. {@code .withArgon2()} with no explicit
 * parameters silently fell back to whatever {@link com.password4j.AlgorithmFinder}
 * resolves when no {@code psw4j.properties} is on the classpath (this project has
 * none - every hash logged half a dozen "default value is used" warnings), and its
 * memory-hard cost scales with the container's actual CPU allocation: unconstrained
 * locally it was fast, but under the CPU share a real deployed container gets, the
 * same call took over an order of magnitude longer, which is what turned a signup
 * into a request that only sometimes finished before its caller gave up. BCrypt's
 * cost factor is fixed here (12, ~250ms/hash on modest hardware) instead of
 * varying by environment, and per-hash salting is built into its own output
 * encoding, so no separate salt call is needed the way Argon2's builder required.
 */
@Component
public class PasswordHasher {

    private static final HashingFunction BCRYPT = BcryptFunction.getInstance(Bcrypt.B, 12);

    public String hash(String rawPassword) {
        return Password.hash(rawPassword).with(BCRYPT).getResult();
    }

    /**
     * False for a missing/blank/malformed hash rather than throwing: a stored hash
     * that isn't a real BCrypt encoding (e.g. a pre-existing row seeded before this
     * fix) is exactly "this password doesn't match," not a server error.
     */
    public boolean matches(String rawPassword, String hash) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return Password.check(rawPassword, hash).with(BCRYPT);
        } catch (BadParametersException e) {
            return false;
        }
    }
}
