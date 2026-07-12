package dev.cairn.api.collab;

import dev.cairn.api.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A conversation comment, attached to exactly one of an issue or a pull request. */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Issue issue;

    @ManyToOne
    private PullRequest pullRequest;

    @ManyToOne
    private User author;

    private String body;

    protected Comment() {
    }

    public static Comment onIssue(Issue issue, User author, String body) {
        Comment comment = new Comment();
        comment.issue = issue;
        comment.author = author;
        comment.body = body;
        return comment;
    }

    public static Comment onPullRequest(PullRequest pullRequest, User author, String body) {
        Comment comment = new Comment();
        comment.pullRequest = pullRequest;
        comment.author = author;
        comment.body = body;
        return comment;
    }

    public Long id() {
        return id;
    }

    public User author() {
        return author;
    }

    public String body() {
        return body;
    }
}
