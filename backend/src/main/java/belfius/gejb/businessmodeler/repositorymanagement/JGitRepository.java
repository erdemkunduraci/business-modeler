package belfius.gejb.businessmodeler.repositorymanagement;

import belfius.gejb.businessmodeler.model.ModelRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A convenience wrapper around JGit that exposes common Git repository functionality.
 *
 * This class supports:
 * - init, clone, open
 * - status
 * - add (all)
 * - commit
 * - branches: list, create, checkout
 * - pull, push
 * - log
 *
 * Extend this class with more operations as needed.
 */


@AllArgsConstructor
public class JGitRepository implements Closeable {

    private final Path workingDir;
    private final Git git;
    private final ModelRepository modelRepository;


    public JGitRepository(ModelRepository modelRepository){
        this.modelRepository = modelRepository;
        this.workingDir = Path.of(modelRepository.getPath());
        try {
            this.git = initGitRepository(workingDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Clone a remote repository into the given local directory.
     */
    public JGitRepository cloneRepository(ModelRepository modelRepository) throws GitAPIException, IOException {
        Git gitRepo = Git.cloneRepository()
                .setURI(modelRepository.getRemoteUrl())
                .setDirectory(Path.of(modelRepository.getPath()).toFile())
                .setCredentialsProvider(credentials(modelRepository.getUsername(), modelRepository.getPassword()))
                .call();

        return new JGitRepository(workingDir, gitRepo, modelRepository);
    }



    private static Git initGitRepository(Path workingDir) throws IOException {
        if (!Files.exists(workingDir)) {
            Files.createDirectories(workingDir);
        }

        if (!isGitRepo(workingDir.toFile())) {
            try {
                return Git.init()
                        .setDirectory(workingDir.toFile())
                        .call();
            } catch (GitAPIException e) {
                throw new IOException("Failed to initialize git repository at " + workingDir, e);
            }
        }

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder
                .setWorkTree(workingDir.toFile())
                .readEnvironment()
                .findGitDir(workingDir.toFile())
                .build();

        return new Git(repository);
    }

    public Path getWorkingDir() {
        return workingDir;
    }

    public Repository getRepository() {
        return git.getRepository();
    }

    private static UsernamePasswordCredentialsProvider credentials(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider(username, password);
    }

    // ------------------------------------------------------------
    // Basic operations
    // ------------------------------------------------------------

    /**
     * git status
     */
    public Status status() throws GitAPIException {
        return git.status().call();
    }

    /**
     * git add --all
     */
    public void addAll() throws GitAPIException {
        git.add()
                .addFilepattern(".")
                .call();
    }

    /**
     * git commit -m "message"
     */
    public RevCommit commit(String message, String authorName, String authorEmail) throws GitAPIException {
        PersonIdent author = new PersonIdent(authorName, authorEmail);
        return git.commit()
                .setMessage(message)
                .setAuthor(author)
                .call();
    }

    /**
     * Creates a new branch.
     *
     * @param branchName the name of the new branch, e.g. "feature/foo"
     * @param checkout   whether to check out the new branch immediately
     */
    public void createBranch(String branchName, boolean checkout) throws GitAPIException {
        git.branchCreate()
                .setName(branchName)
                .call();

        if (checkout) {
            checkout(branchName);
        }
    }

    /**
     * Push a single branch to the configured remote.
     *
     * @param branchName branch to push (refs/heads will be prefixed automatically)
     */
    public void pushBranch(String branchName, String username, String password) throws GitAPIException {
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("branchName must not be blank");
        }

        git.push()
                .setCredentialsProvider(credentials(username, password))
                .add("refs/heads/" + branchName)
                .call();
    }

    /**
     * git checkout <branchName>
     */
    public void checkout(String branchName) throws GitAPIException {
        git.checkout()
                .setName(branchName)
                .call();

    }


    /**
     * List local branches.
     */
    public List<String> listLocalBranches() throws GitAPIException {
        List<String> result = new ArrayList<>();
        git.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call()
                .forEach(ref -> result.add(ref.getName()));
        return result;
    }

    /**
     * git pull
     */
    public void pull(String username, String password) throws GitAPIException {
        git.pull()
                .setCredentialsProvider(credentials(username, password))
                .call();
    }

    /**
     * git push
     */
    public void push(String username, String password) throws GitAPIException {
        git.push()
                .setCredentialsProvider(credentials(username, password))
                .setPushAll()
                .call();

    }

    /**
     * git log
     *
     * @param maxCommits maximum number of commits to return
     */
    public List<RevCommit> log(int maxCommits) throws GitAPIException {
        Iterable<RevCommit> log = git.log().setMaxCount(maxCommits).call();
        List<RevCommit> commits = new ArrayList<>();
        for (RevCommit commit : log) {
            commits.add(commit);
        }
        return commits;
    }

    /**
     * Find the first file in the repository that matches the given filename (searches recursively).
     *
     * @param fileName file name to look for (no path required)
     * @return matching File or null when not found
     */
    public File findFileByName(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be null or blank");
        }
        if (!Files.exists(workingDir)) {
            return null;
        }

        try (Stream<Path> paths = Files.walk(workingDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst()
                    .map(Path::toFile)
                    .orElse(null);
        }
    }

    // ------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------

    @Override
    public void close() {
        git.close();
    }

    // ------------------------------------------------------------
    // Example usage
    // ------------------------------------------------------------

    public static boolean isGitRepo(File dir) {
        try (Repository repo = new FileRepositoryBuilder()
                .setWorkTree(dir)
                .findGitDir(dir)          // climbs parents to locate .git
                .build()) {
            return repo.getDirectory() != null; // .git found and parsed
        } catch (IOException e) {
            return false; // not a repo or unreadable
        }
    }
    public static void main(String[] args) throws IOException, GitAPIException {
        // Example usage; adjust paths and credentials as needed.
        //Path repoDir = Path.of("/path/to/local/repo");
/*        Path repoDir = Path.of("C:\\Coding\\dmn");

        String remoteUrl = "https://pennasoft-test@dev.azure.com/pennasoft-test/SmartNavigation-test/_git/dmn-test";
        boolean clone = false;
        if(Files.isDirectory(repoDir)) {
            if(!isGitRepo(repoDir.toFile())){
                clone = true;
            }
        }else{
           Files.createDirectory(repoDir);
           clone = true;
        }
        JGitRepository gitRepository = clone ? JGitRepository.cloneRepository(remoteUrl, repoDir, token, "") : JGitRepository.open(repoDir);
        gitRepository.getRepository().getFullBranch();
        if(gitRepository.status().hasUncommittedChanges()){
            gitRepository.addAll();
            gitRepository.commit("first commit", "erdem", "");
            gitRepository.push("erdem", token);
        }*/


/*        try (JGitRepository repo = JGitRepository.open(repoDir)) {

            // Status
            Status status = repo.status();
            System.out.println("Added: " + status.getAdded());
            System.out.println("Modified: " + status.getModified());
            System.out.println("Untracked: " + status.getUntracked());

            // Stage all and commit
            repo.addAll();
            RevCommit commit = repo.commit(
                    "My commit message",
                    "cd ../Your Name",
                    "you@example.com"
            );
            System.out.println("Committed: " + commit.getId().getName());

            // Show log
            for (RevCommit c : repo.log(10)) {
                System.out.println(c.getId().getName() + " " + c.getShortMessage());
            }

        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }*/
    }
}
