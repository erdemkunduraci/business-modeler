package belfius.gejb.dmntool;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
public class JGitRepository implements Closeable {

    private final Path workingDir;
    private final Git git;

    /**
     * Initialize a new Git repository at the given path.
     */
    public static JGitRepository init(Path directory) throws GitAPIException {
        Git git = Git.init()
                .setDirectory(directory.toFile())
                .call();

        return new JGitRepository(directory, git);
    }

    /**
     * Clone a remote repository into the given local directory.
     */
    public static JGitRepository cloneRepository(
            String remoteUrl,
            Path directory,
            String username,
            String password
    ) throws GitAPIException {
        Git git = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(directory.toFile())
                .setCredentialsProvider(credentials(username, password))
                .call();

        return new JGitRepository(directory, git);
    }

    /**
     * Open an existing repository (directory must contain a .git folder).
     */
    public static JGitRepository open(Path directory) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder
                .setWorkTree(directory.toFile())
                .readEnvironment()
                .findGitDir(directory.toFile())
                .build();

        Git git = new Git(repository);
        return new JGitRepository(directory, git);
    }

    private JGitRepository(Path workingDir, Git git) {
        this.workingDir = workingDir;
        this.git = git;
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
     * Current branch name.
     */
    public String getCurrentBranch() throws IOException {
        return git.getRepository().getBranch();
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

    public static void main(String[] args) {
        // Example usage; adjust paths and credentials as needed.
        Path repoDir = Path.of("/path/to/local/repo");

        try (JGitRepository repo = JGitRepository.open(repoDir)) {

            // Status
            Status status = repo.status();
            System.out.println("Added: " + status.getAdded());
            System.out.println("Modified: " + status.getModified());
            System.out.println("Untracked: " + status.getUntracked());

            // Stage all and commit
            repo.addAll();
            RevCommit commit = repo.commit(
                    "My commit message",
                    "Your Name",
                    "you@example.com"
            );
            System.out.println("Committed: " + commit.getId().getName());

            // Show log
            for (RevCommit c : repo.log(10)) {
                System.out.println(c.getId().getName() + " " + c.getShortMessage());
            }

        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
    }
}
