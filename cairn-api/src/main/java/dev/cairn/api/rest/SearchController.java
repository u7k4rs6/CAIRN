package dev.cairn.api.rest;

import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.api.search.RepoSearchIndexService;
import dev.cairn.vcs.search.TrigramIndex;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FR-SEARCH-1 and frontend spec section 5.8: code search scoped to a repo the
 * caller can read, gated exactly like every other repo endpoint, so a private
 * repo's contents cannot leak through search results either.
 */
@RestController
@RequestMapping("/api/repos/{owner}/{name}")
public class SearchController {

    private final RepoJpaRepository repos;
    private final PrincipalResolver principalResolver;
    private final PermissionResolver permissionResolver;
    private final RepoSearchIndexService searchIndexService;

    public SearchController(RepoJpaRepository repos, PrincipalResolver principalResolver,
                             PermissionResolver permissionResolver, RepoSearchIndexService searchIndexService) {
        this.repos = repos;
        this.principalResolver = principalResolver;
        this.permissionResolver = permissionResolver;
        this.searchIndexService = searchIndexService;
    }

    public record SearchResponse(boolean indexing, boolean queryTooShort, List<TrigramIndex.FileMatch> results) {
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@PathVariable String owner, @PathVariable String name,
                                     @RequestParam(required = false) String q, HttpServletRequest request) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        if (repo == null || !permissionResolver.authorize(principalResolver.resolve(request), repo, Role.READ)) {
            return ResponseEntity.notFound().build();
        }
        if (q == null || q.length() < 3) {
            return ResponseEntity.ok(new SearchResponse(false, true, List.of()));
        }
        var outcome = searchIndexService.search(owner, name, q);
        return ResponseEntity.ok(new SearchResponse(outcome.indexing(), false, outcome.matches()));
    }
}
