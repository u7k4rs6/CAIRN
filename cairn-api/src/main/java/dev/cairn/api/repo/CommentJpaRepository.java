package dev.cairn.api.repo;

import dev.cairn.api.collab.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentJpaRepository extends JpaRepository<Comment, Long> {
}
