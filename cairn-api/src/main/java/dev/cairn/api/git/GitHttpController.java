package dev.cairn.api.git;

import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.BranchProtectionRuleJpaRepository;
import dev.cairn.transfer.ReceivePackHandler;
import dev.cairn.transfer.RefAdvertisement;
import dev.cairn.transfer.TransferCapabilities;
import dev.cairn.transfer.UploadPackHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Git smart-HTTP endpoints (architecture doc, section 7.2): ref advertisement for
 * both services, and the upload-pack/receive-pack request bodies.
 *
 * <p><b>Authorization (security doc, section 6.3).</b> Fetch/clone requires read;
 * push requires write, checked up front (since pushing anonymously never makes
 * sense) and then per ref inside {@link GitReceivePackAuthorizer} (section 4.3),
 * which also enforces branch protection. A denial for an anonymous principal comes
 * back as {@code 401} with a {@code WWW-Authenticate} challenge, exactly the signal
 * a real Git client needs to retry the request with the credentials embedded in its
 * remote URL; a denial for an authenticated-but-insufficient principal comes back as
 * {@code 404}, so a repo that does not exist and one the principal cannot see are
 * indistinguishable, as the security doc requires.
 */
@RestController
public class GitHttpController {

    private final RepositoryRegistry repositories;
    private final RepoService repoService;
    private final PrincipalResolver principalResolver;
    private final PermissionResolver permissionResolver;
    private final BranchProtectionRuleJpaRepository branchProtectionRules;

    public GitHttpController(RepositoryRegistry repositories, RepoService repoService,
                              PrincipalResolver principalResolver, PermissionResolver permissionResolver,
                              BranchProtectionRuleJpaRepository branchProtectionRules) {
        this.repositories = repositories;
        this.repoService = repoService;
        this.principalResolver = principalResolver;
        this.permissionResolver = permissionResolver;
        this.branchProtectionRules = branchProtectionRules;
    }

    @GetMapping(value = "/{owner}/{repo}/info/refs")
    public ResponseEntity<byte[]> infoRefs(
            @PathVariable String owner, @PathVariable String repo, @RequestParam String service,
            HttpServletRequest request) {
        Repo repoMeta = repoService.resolveOrCreate(owner, stripDotGit(repo));
        Principal principal = principalResolver.resolve(request);
        Role required = service.equals("git-receive-pack") ? Role.WRITE : Role.READ;
        if (!permissionResolver.authorize(principal, repoMeta, required)) {
            return denyResponse(principal);
        }

        var handle = repositories.resolve(owner, stripDotGit(repo));
        String capabilities = service.equals("git-receive-pack")
                ? TransferCapabilities.RECEIVE_PACK
                : TransferCapabilities.UPLOAD_PACK;
        byte[] body = RefAdvertisement.build(service, handle.refStore().list(), capabilities);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/x-" + service + "-advertisement")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(body);
    }

    @PostMapping(value = "/{owner}/{repo}/git-upload-pack")
    public ResponseEntity<byte[]> uploadPack(
            @PathVariable String owner, @PathVariable String repo, HttpServletRequest request) throws IOException {
        Repo repoMeta = repoService.resolveOrCreate(owner, stripDotGit(repo));
        Principal principal = principalResolver.resolve(request);
        if (!permissionResolver.authorize(principal, repoMeta, Role.READ)) {
            return denyResponse(principal);
        }

        var handle = repositories.resolve(owner, stripDotGit(repo));
        UploadPackHandler.Request parsed = UploadPackHandler.parseRequest(request.getInputStream());
        byte[] response = UploadPackHandler.buildResponse(handle.objectStore(), parsed);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/x-git-upload-pack-result")
                .body(response);
    }

    @PostMapping(value = "/{owner}/{repo}/git-receive-pack")
    public ResponseEntity<byte[]> receivePack(
            @PathVariable String owner, @PathVariable String repo, HttpServletRequest request) throws IOException {
        Repo repoMeta = repoService.resolveOrCreate(owner, stripDotGit(repo));
        Principal principal = principalResolver.resolve(request);
        if (!permissionResolver.authorize(principal, repoMeta, Role.WRITE)) {
            return denyResponse(principal);
        }

        var handle = repositories.resolve(owner, stripDotGit(repo));
        var authorizer = new GitReceivePackAuthorizer(permissionResolver, principal, repoMeta,
                branchProtectionRules.findByRepo(repoMeta), handle.objectStore(), handle.generations());
        ReceivePackHandler.Outcome outcome = ReceivePackHandler.handle(
                handle.objectStore(), handle.refStore(), handle.generations(), request.getInputStream(), authorizer);
        byte[] response = ReceivePackHandler.buildReportStatus(outcome);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/x-git-receive-pack-result")
                .body(response);
    }

    /** Anonymous gets a 401 challenge (so a real client retries with credentials); anyone else denied gets a masked 404. */
    private ResponseEntity<byte[]> denyResponse(Principal principal) {
        if (principal instanceof Principal.AnonymousPrincipal) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"cairn\"")
                    .build();
        }
        return ResponseEntity.notFound().build();
    }

    private String stripDotGit(String repo) {
        return repo.endsWith(".git") ? repo.substring(0, repo.length() - 4) : repo;
    }
}
