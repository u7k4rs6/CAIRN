package dev.cairn.api.repo;

import dev.cairn.api.collab.Comment;
import dev.cairn.api.collab.PullRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentJpaRepository extends JpaRepository<Comment, Long> {

    /** Ascending id as an insertion-order proxy: comments carry no timestamp column, and adding one is a schema change out of scope here. */
    List<Comment> findByPullRequestOrderByIdAsc(PullRequest pullRequest);
}
