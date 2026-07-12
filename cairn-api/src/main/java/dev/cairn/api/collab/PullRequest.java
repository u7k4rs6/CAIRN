package dev.cairn.api.collab;

import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Proposes merging {@code sourceRef} into {@code targetRef}; carries a lifecycle state and, once merged, the strategy used (architecture doc, section 5). */
@Entity
@Table(name = "pull_requests")
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Repo repo;

    @ManyToOne
    private User author;

    private String title;
    private String sourceRef;
    private String targetRef;

    @Enumerated(EnumType.STRING)
    private PullRequestState state = PullRequestState.OPEN;

    @Enumerated(EnumType.STRING)
    private MergeStrategy mergeStrategy;

    protected PullRequest() {
    }

    public PullRequest(Repo repo, User author, String title, String sourceRef, String targetRef) {
        this.repo = repo;
        this.author = author;
        this.title = title;
        this.sourceRef = sourceRef;
        this.targetRef = targetRef;
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

    public String sourceRef() {
        return sourceRef;
    }

    public String targetRef() {
        return targetRef;
    }

    public PullRequestState state() {
        return state;
    }

    public void transitionTo(PullRequestState next) {
        this.state = next;
    }

    public MergeStrategy mergeStrategy() {
        return mergeStrategy;
    }

    public void setMergeStrategy(MergeStrategy mergeStrategy) {
        this.mergeStrategy = mergeStrategy;
    }

    public void assignIdForTesting(Long id) {
        this.id = id;
    }
}
