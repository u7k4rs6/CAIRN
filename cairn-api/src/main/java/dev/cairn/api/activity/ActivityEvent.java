package dev.cairn.api.activity;

/**
 * One thing that happened on a repo (a push, an opened issue, a merged pull
 * request), the notification an {@link ActivityListener} reacts to. Deliberately
 * flat and repo-scoped: every current listener (the in-memory feed, logging) only
 * needs to know what happened and where, not a typed hierarchy of event classes.
 */
public record ActivityEvent(Long repoId, String repoFullName, String actorUsername, String type, String summary,
                             long timestampEpochSeconds) {
}
