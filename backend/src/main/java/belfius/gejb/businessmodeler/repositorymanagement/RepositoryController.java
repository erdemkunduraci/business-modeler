package belfius.gejb.businessmodeler.repositorymanagement;

import belfius.gejb.businessmodeler.model.ModelRepository;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/repository-management")
public class RepositoryController {

    private final RepositoryConfigurationService repositoryConfigurationService;
    private final RepositoryManager repositoryManager;

    public RepositoryController(RepositoryConfigurationService repositoryConfigurationService, RepositoryManager repositoryManager) {
        this.repositoryConfigurationService = repositoryConfigurationService;
        this.repositoryManager = repositoryManager;
    }

    /**
     * List branches for the repository identified by {@code projectCode} and {@code repositoryName}.
     * <p>
     * The repositoryName is resolved as a path on the server. The endpoint returns 404 when the
     * path does not exist and 400 when the path is not a Git repository.
     */
    @GetMapping("/{projectCode}/{repositoryName}/branches")
    public ResponseEntity<List<String>> listBranches(@PathVariable String projectCode, @PathVariable String repositoryName) {
        Optional<ModelRepository> modelRepositoryOpt = repositoryConfigurationService.findByProjectCodeAndName(projectCode, repositoryName);
        if (modelRepositoryOpt.isEmpty()) {
            log.warn("Repository not found for projectCode={} repositoryName={}", projectCode, repositoryName);
            return ResponseEntity.notFound().build();
        }
        Path repositoryPath = Path.of(modelRepositoryOpt.get().getPath());
        if (!JGitRepository.isGitRepo(repositoryPath.toFile())) {
            log.warn("Path is not a git repository: {}", repositoryPath);
            return ResponseEntity.badRequest().build();
        }

        try {
            return ResponseEntity.ok(repositoryManager.listBranches(modelRepositoryOpt.get()));
        } catch (GitAPIException e) {
            log.error("Failed to list branches for repository {}", repositoryName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Create a new branch in the repository identified by {@code projectCode} and {@code repositoryName}.
     */
    @PostMapping("/{projectCode}/{repositoryName}/create-branch")
    public ResponseEntity<Void> createBranch(
            @PathVariable String projectCode,
            @PathVariable String repositoryName,
            @RequestBody CreateBranchRequest request
    ) {
        Optional<ModelRepository> modelRepositoryOpt = repositoryConfigurationService.findByProjectCodeAndName(projectCode, repositoryName);
        if (modelRepositoryOpt.isEmpty()) {
            log.warn("Repository not found for projectCode={} repositoryName={} during createBranch", projectCode, repositoryName);
            return ResponseEntity.notFound().build();
        }
        if (request == null || request.getBranchName() == null || request.getBranchName().isBlank()) {
            log.warn("Invalid createBranch request payload for repository {}", repositoryName);
            return ResponseEntity.badRequest().build();
        }

        ResponseEntity error = checkForError(modelRepositoryOpt.map(ModelRepository::getPath));
        if (error != null) {
            return error;
        }

        try {
            repositoryManager.createBranch(request.getBranchName(), request.getSourceBranch(), modelRepositoryOpt.get());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid branch creation parameters for repository {}", repositoryName, e);
            return ResponseEntity.badRequest().build();
        } catch (GitAPIException e) {
            log.error("Git error while creating branch {} in repository {}", request.getBranchName(), repositoryName, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Write a file to a repository and commit it on the specified branch.
     */
    @PostMapping(value = "/{projectCode}/{repositoryName}/commit-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> commitFile(
            @PathVariable String projectCode,
            @PathVariable String repositoryName,
            @ModelAttribute CommitFileRequest request
    ) {
        Optional<ModelRepository> modelRepository = repositoryConfigurationService.findByProjectCodeAndName(projectCode, repositoryName);
        Optional<String> repositoryPathOpt = modelRepository
                .map(repo -> repo.getPath());

        ResponseEntity error = checkForError(repositoryPathOpt);
        if (error != null) {
            return error;
        }

        try {
           repositoryManager.commitFile(request, modelRepository.get());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid commit request for repository {}", repositoryName, e);
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("IO error committing file to repository {}", repositoryName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (GitAPIException e) {
            log.error("Git error committing file to repository {}", repositoryName, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Return a file's content as base64 for the given repository and branch.
     */
    @GetMapping("/{projectCode}/{repositoryName}/file")
    public ResponseEntity<String> getFile(
            @PathVariable String projectCode,
            @PathVariable String repositoryName,
            @RequestParam("branch") String branch,
            @RequestParam("fileName") String fileName
    ) {
        Optional<ModelRepository> modelRepositoryOpt = repositoryConfigurationService.findByProjectCodeAndName(projectCode, repositoryName);


        if (modelRepositoryOpt.isEmpty()) {
            log.warn("Repository not found for projectCode={} repositoryName={} during getFile", projectCode, repositoryName);
            return ResponseEntity.notFound().build();
        }
        try {
            File content = repositoryManager.getFile(fileName, modelRepositoryOpt.get(), branch);
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(content.toPath()));
            return ResponseEntity.ok(base64);
        } catch (IOException e) {
            log.error("IO error retrieving file {} from repository {}", fileName, repositoryName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (GitAPIException e) {
            log.error("Git error retrieving file {} from repository {}", fileName, repositoryName, e);
            throw new RuntimeException(e);
        }
    }

    private ResponseEntity checkForError(Optional<String> repositoryPathOpt) {
        if (repositoryPathOpt.isEmpty()) {
            log.warn("Repository path missing during validation");
            return ResponseEntity.notFound().build();
        }

        Path repositoryPath = Path.of(repositoryPathOpt.get());
        if (!Files.exists(repositoryPath)) {
            log.warn("Repository path does not exist: {}", repositoryPath);
            return ResponseEntity.notFound().build();
        }
        if (!JGitRepository.isGitRepo(repositoryPath.toFile())) {
            log.warn("Repository path is not a git repository: {}", repositoryPath);
            return ResponseEntity.badRequest().build();
        }
        return null;
    }

    /**
     * List files under a repository path for the given branch.
     */
    @GetMapping("/{projectCode}/{repositoryName}/files")
    public ResponseEntity<List<String>> listFiles(
            @PathVariable String projectCode,
            @PathVariable String repositoryName,
            @RequestParam("branch") String branch
    ) {
        Optional<ModelRepository> modelRepositoryOpt = repositoryConfigurationService.findByProjectCodeAndName(projectCode, repositoryName);
        if (modelRepositoryOpt.isEmpty()) {
            log.warn("Repository not found for projectCode={} repositoryName={} during listFiles", projectCode, repositoryName);
            return ResponseEntity.notFound().build();
        }
        modelRepositoryOpt.get().setMainBranch(branch);

        try {
            List<String> files = repositoryManager.listFiles(modelRepositoryOpt.get());

            return ResponseEntity.ok(files);
        }catch (IOException | GitAPIException e) {
            log.error("Error listing files for repository {} on branch {}", repositoryName, branch, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
