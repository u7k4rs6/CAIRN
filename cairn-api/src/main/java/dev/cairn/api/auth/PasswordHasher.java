package dev.cairn.api.auth;

import com.password4j.Password;
import org.springframework.stereotype.Component;

/** Argon2id, per-user salt (security doc, section 2.2): memory-hard, never a fast hash, never plaintext. */
@Component
public class PasswordHasher {

    public String hash(String rawPassword) {
        return Password.hash(rawPassword).addRandomSalt().withArgon2().getResult();
    }

    public boolean matches(String rawPassword, String hash) {
        return Password.check(rawPassword, hash).withArgon2();
    }
}
