package dev.cairn.api.collab;

import dev.cairn.api.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A reviewer's approve/request-changes/comment verdict on a pull request, optionally anchored to a line (FR-COLLAB-3). */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private PullRequest pullRequest;

    @ManyToOne
    private User reviewer;

    @Enumerated(EnumType.STRING)
    private ReviewVerdict verdict;

    private String body;
    private String path;
    private Integer line;

    protected Review() {
    }

    public Review(PullRequest pullRequest, User reviewer, ReviewVerdict verdict, String body, String path, Integer line) {
        this.pullRequest = pullRequest;
        this.reviewer = reviewer;
        this.verdict = verdict;
        this.body = body;
        this.path = path;
        this.line = line;
    }

    public Long id() {
        return id;
    }

    public PullRequest pullRequest() {
        return pullRequest;
    }

    public User reviewer() {
        return reviewer;
    }

    public ReviewVerdict verdict() {
        return verdict;
    }

    public String body() {
        return body;
    }

    public String path() {
        return path;
    }

    public Integer line() {
        return line;
    }
}
