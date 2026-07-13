package dev.cairn.api.repo;

import dev.cairn.api.collab.Issue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * With {@code spring.jpa.open-in-view} disabled, no Hibernate session survives
 * past this call into response serialization, so {@code labels} and
 * {@code assignees} (the only {@code @ManyToMany}, lazy-by-default associations
 * {@link Issue} carries) must be fetched in the same query as the entity itself,
 * not lazily afterward.
 *
 * <p><b>{@code EntityGraphType.LOAD}, not the default {@code FETCH}.</b> The
 * default type replaces the query's entire fetch plan: every attribute not named
 * in the graph becomes lazy, even ones mapped eager by default. That silently
 * turned {@code Issue.repo} and {@code Issue.author} (plain {@code @ManyToOne},
 * eager by JPA default) into unfetched proxies, which then threw
 * {@code LazyInitializationException} during serialization - a real bug this
 * change caused and caught immediately via the full test suite, not a
 * hypothetical. {@code LOAD} only adds the named paths as eager and leaves every
 * other attribute's own mapped fetch type alone.
 */
public interface IssueJpaRepository extends JpaRepository<Issue, Long> {

    @Override
    @EntityGraph(attributePaths = {"labels", "assignees", "milestone"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Issue> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"labels", "assignees", "milestone"}, type = EntityGraph.EntityGraphType.LOAD)
    List<Issue> findAll();
}
