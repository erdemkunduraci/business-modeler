package belfius.gejb.businessmodeler.repositorymanagement;

import belfius.gejb.businessmodeler.model.ModelRepository;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RepositoryManager {

    public static final String LOCAL_BRANCH_PREFIX = "refs/heads/";
    public static final String REMOTE_BRANCH_PREFIX = "refs/remotes/origin/";

    public List<String> listBranches(ModelRepository modelRepository) throws GitAPIException {
        log.info("Listing branches for repository {}", modelRepository.getName());
        JGitRepository jGitRepository = new JGitRepository(modelRepository);
        return jGitRepository.listLocalBranches();
    }

    public void createBranch(String branchName, String sourceBranch, ModelRepository modelRepository) throws GitAPIException {

        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("branchName must not be blank");
        }
        JGitRepository jGitRepository = new JGitRepository(modelRepository);
        String branchToCheckout = sourceBranch != null && !sourceBranch.isBlank()
                ? sourceBranch
                : modelRepository.getMainBranch();

        log.info("Creating branch '{}' in repository {} from {}", branchName, modelRepository.getName(), branchToCheckout);
        if (branchToCheckout != null && !branchToCheckout.isBlank()) {
            jGitRepository.checkout(branchToCheckout);
        }
        branchName = branchName.replace(LOCAL_BRANCH_PREFIX, "");
        branchName = branchName.replace(REMOTE_BRANCH_PREFIX, "");
        String localBranchName = LOCAL_BRANCH_PREFIX + branchName;
        String remoteBranchName = REMOTE_BRANCH_PREFIX + branchName;
        List<String> branchList = listBranches(modelRepository);
        if(!branchList.contains(localBranchName)) {
            log.debug("Local branch '{}' does not exist, creating", localBranchName);
            jGitRepository.createBranch(branchName, false);
        } else {
            log.debug("Local branch '{}' already exists, skipping creation", localBranchName);
        }
        if(!branchList.contains(remoteBranchName)) {
            log.debug("Remote branch '{}' does not exist, pushing new branch", remoteBranchName);
            jGitRepository.pushBranch(branchName, modelRepository.getUsername(), modelRepository.getPassword());
        } else {
            log.debug("Remote branch '{}' already exists, skipping push", remoteBranchName);
        }
        log.info("Branch '{}' processed for repository {}", branchName, modelRepository.getName());
    }

    public File getFile(String fileName, ModelRepository modelRepository, String branch) throws IOException, GitAPIException {
        log.info("Retrieving file '{}' from branch '{}' in repository {}", fileName, branch, modelRepository.getName());
        JGitRepository jGitRepository = new JGitRepository(modelRepository);
        if (!branch.equals(modelRepository.getMainBranch())) {
            jGitRepository.checkout(branch);
        }
        return jGitRepository.findFileByName(fileName);
    }

    public void commitFile(CommitFileRequest commitFileRequest, ModelRepository modelRepository) throws IOException, GitAPIException {
        if (commitFileRequest.getFile() == null || commitFileRequest.getFile().isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        String originalFileName = commitFileRequest.getFile().getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("file name must not be blank");
        }
        String branch = StringUtils.isEmpty(commitFileRequest.getBranch()) ? modelRepository.getMainBranch() : commitFileRequest.getBranch();
        log.info("Committing file '{}' to branch '{}' in repository {}", originalFileName, branch, modelRepository.getName());
        JGitRepository jGitRepository = new JGitRepository(modelRepository);
        jGitRepository.checkout(branch);
        Path repositoryPath = Path.of(modelRepository.getPath());
        Path targetFile = repositoryPath.resolve(originalFileName).normalize();
        if (!targetFile.startsWith(repositoryPath)) {
            throw new IllegalArgumentException("file path must stay within repository");
        }
        Files.createDirectories(targetFile.getParent());
        Files.write(
                targetFile,
                commitFileRequest.getFile().getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        jGitRepository.addAll();
        String authorName = commitFileRequest.getAuthorName() == null ? modelRepository.getDefaultCommitUser() : commitFileRequest.getAuthorName();
        String authorEmail = commitFileRequest.getAuthorEmail() == null ? "" : commitFileRequest.getAuthorEmail();
        jGitRepository.commit(
                commitFileRequest.getCommitMessage(),
                authorName,
                authorEmail
        );
        jGitRepository.pushBranch(branch, modelRepository.getUsername(), modelRepository.getPassword());
        log.info("File '{}' committed to branch '{}' in repository {}", originalFileName, branch, modelRepository.getName());
    }

    public List<String> listFiles(ModelRepository modelRepository) throws IOException, GitAPIException {
        log.info("Listing files for repository {} on branch {}", modelRepository.getName(), modelRepository.getMainBranch());
        JGitRepository jGitRepository = new JGitRepository(modelRepository);
        if(!modelRepository.getMainBranch().equals(jGitRepository.getRepository().getBranch())) {
            jGitRepository.checkout(modelRepository.getMainBranch());
        }
        Path repositoryPath = Path.of(modelRepository.getPath());
        List<String> files = Files.list(repositoryPath)
                .map(path -> repositoryPath.relativize(path).toString())
                .collect(Collectors.toList());
        return files;
    }

}
