package dev.cairn.api.repo;

import dev.cairn.api.collab.PullRequest;
import dev.cairn.api.collab.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewJpaRepository extends JpaRepository<Review, Long> {
    List<Review> findByPullRequest(PullRequest pullRequest);
}
