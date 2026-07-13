package dev.cairn.api.collab;

import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

/** FR-COLLAB-1: a unit of tracked work on a repo, open to anyone who can read it. */
@Entity
@Table(name = "issues")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Repo repo;

    @ManyToOne
    private User author;

    private String title;
    private String body;

    @Enumerated(EnumType.STRING)
    private IssueState state = IssueState.OPEN;

    @ManyToMany
    @JoinTable(name = "issue_labels")
    private Set<Label> labels = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "issue_assignees")
    private Set<User> assignees = new HashSet<>();

    @ManyToOne
    private Milestone milestone;

    protected Issue() {
    }

    public Issue(Repo repo, User author, String title, String body) {
        this.repo = repo;
        this.author = author;
        this.title = title;
        this.body = body;
    }

    public Long id() {
        return id;
    }

    public Repo repo() {
        return repo;
    }

    public User author() {
        return author;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public IssueState state() {
        return state;
    }

    public void transitionTo(IssueState next) {
        this.state = next;
    }

    public Set<Label> labels() {
        return labels;
    }

    public void addLabel(Label label) {
        labels.add(label);
    }

    /**
     * Removes by matching {@code id}, not {@code Set.remove(label)}'s default
     * object-identity {@code equals}: {@code label} here and the element already
     * inside {@code labels} were loaded by two separate repository calls (with
     * {@code spring.jpa.open-in-view} off, each gets its own Hibernate session, no
     * shared first-level cache to deduplicate them), so they are two distinct Java
     * objects for the same database row. {@code Label}/{@code User} deliberately
     * do not override {@code equals}/{@code hashCode} (matching every other entity
     * in this codebase), so identity-based {@code Set.remove} would silently no-op
     * here - a real bug this project's own open-in-view change surfaced, not a
     * hypothetical.
     */
    public void removeLabel(Label label) {
        labels.removeIf(l -> l.id() != null && l.id().equals(label.id()));
    }

    public Set<User> assignees() {
        return assignees;
    }

    public void addAssignee(User user) {
        assignees.add(user);
    }

    /** See {@link #removeLabel}'s Javadoc: the same identity-vs-id pitfall applies here. */
    public void removeAssignee(User user) {
        assignees.removeIf(a -> a.id() != null && a.id().equals(user.id()));
    }

    public Milestone milestone() {
        return milestone;
    }

    public void setMilestone(Milestone milestone) {
        this.milestone = milestone;
    }
}
