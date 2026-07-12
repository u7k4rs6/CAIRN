package dev.cairn.vcs.object;

/**
 * An author or committer identity: name, email, and a point in time expressed the way
 * Git expresses it, seconds since the epoch plus a fixed-offset timezone, so the same
 * commit hashes identically no matter which machine or timezone re-serializes it.
 */
public record PersonIdent(String name, String email, long epochSeconds, String timezoneOffset) {

    public PersonIdent {
        if (name.contains("<") || name.contains(">") || name.contains("\n")) {
            throw new IllegalArgumentException("name must not contain <, >, or newlines: " + name);
        }
        if (email.contains("\n")) {
            throw new IllegalArgumentException("email must not contain newlines: " + email);
        }
    }

    /** Renders as Git does: {@code "Name <email> epochSeconds +ZZZZ"}. */
    String format() {
        return name + " <" + email + "> " + epochSeconds + " " + timezoneOffset;
    }

    static PersonIdent parse(String line) {
        int emailStart = line.indexOf('<');
        int emailEnd = line.indexOf('>', emailStart);
        String name = line.substring(0, emailStart).trim();
        String email = line.substring(emailStart + 1, emailEnd);
        String[] rest = line.substring(emailEnd + 1).trim().split(" ");
        long epochSeconds = Long.parseLong(rest[0]);
        String tz = rest[1];
        return new PersonIdent(name, email, epochSeconds, tz);
    }
}
