package dev.cairn.api.repo;

import dev.cairn.api.collab.PullRequest;
import dev.cairn.api.collab.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewJpaRepository extends JpaRepository<Review, Long> {
    List<Review> findByPullRequest(PullRequest pullRequest);

    /** Ascending id as an insertion-order proxy: reviews carry no timestamp column, and adding one is a schema change out of scope here. */
    List<Review> findByPullRequestOrderByIdAsc(PullRequest pullRequest);
}
