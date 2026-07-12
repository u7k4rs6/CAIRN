package dev.cairn.api.domain;

/**
 * The acting identity on a request (security doc, section 2.1): an authenticated
 * {@link UserPrincipal} or an unauthenticated {@link AnonymousPrincipal}. A sealed
 * interface so {@code effective_role}'s anonymous-vs-user branch is exhaustive and
 * checked by the compiler, not an unguarded cast.
 */
public sealed interface Principal permits Principal.AnonymousPrincipal, Principal.UserPrincipal {

    record AnonymousPrincipal() implements Principal {
    }

    record UserPrincipal(User user) implements Principal {
    }

    AnonymousPrincipal ANONYMOUS = new AnonymousPrincipal();
}
