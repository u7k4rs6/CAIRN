package dev.cairn.api.repo;

import dev.cairn.api.domain.Repo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RepoJpaRepository extends JpaRepository<Repo, Long> {

    // Explicit LEFT JOINs matter here: a bare path expression like `r.ownerOrg.name`
    // in JPQL implicitly INNER JOINs that association, which would silently drop
    // every user-owned repo (ownerOrg is null) from the result entirely, regardless
    // of any "is not null" guard elsewhere in the WHERE clause.
    @Query("select r from Repo r left join r.ownerUser ou left join r.ownerOrg oo "
            + "where r.name = :name and ((ou is not null and ou.username = :owner) or (oo is not null and oo.name = :owner))")
    Optional<Repo> findByOwnerAndName(@Param("owner") String owner, @Param("name") String name);
}
