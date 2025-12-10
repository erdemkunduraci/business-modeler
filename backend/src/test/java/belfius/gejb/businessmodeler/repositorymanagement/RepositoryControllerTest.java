package belfius.gejb.businessmodeler.repositorymanagement;

import belfius.gejb.businessmodeler.model.ModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RepositoryController.class)
class RepositoryControllerTest {

    private static final String PROJECT_CODE = "project";
    private static final String REPO_NAME = "repo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RepositoryConfigurationService repositoryConfigurationService;

    @MockitoBean
    private RepositoryManager repositoryManager;

    @Test
    void listBranches_returnsBranches() throws Exception {
        ModelRepository repo = repoWithPath(initGitRepo());
        when(repositoryConfigurationService.findByProjectCodeAndName(PROJECT_CODE, REPO_NAME)).thenReturn(Optional.of(repo));
        when(repositoryManager.listBranches(repo)).thenReturn(List.of("main", "feature/test"));

        mockMvc.perform(get("/repository-management/{projectCode}/{repositoryName}/branches", PROJECT_CODE, REPO_NAME))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(List.of("main", "feature/test"))));
    }

    @Test
    void listBranches_returnsNotFoundWhenRepoMissing() throws Exception {
        when(repositoryConfigurationService.findByProjectCodeAndName(PROJECT_CODE, REPO_NAME)).thenReturn(Optional.empty());

        mockMvc.perform(get("/repository-management/{projectCode}/{repositoryName}/branches", PROJECT_CODE, REPO_NAME))
                .andExpect(status().isNotFound());
    }

    @Test
    void createBranch_returnsBadRequestWhenBranchMissing() throws Exception {
        ModelRepository repo = repoWithPath(initGitRepo());
        when(repositoryConfigurationService.findByProjectCodeAndName(PROJECT_CODE, REPO_NAME)).thenReturn(Optional.of(repo));

        mockMvc.perform(
                        post("/repository-management/{projectCode}/{repositoryName}/create-branch", PROJECT_CODE, REPO_NAME)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBranch_createsAndReturnsCreated() throws Exception {
        ModelRepository repo = repoWithPath(initGitRepo());
        when(repositoryConfigurationService.findByProjectCodeAndName(PROJECT_CODE, REPO_NAME)).thenReturn(Optional.of(repo));

        mockMvc.perform(
                        post("/repository-management/{projectCode}/{repositoryName}/create-branch", PROJECT_CODE, REPO_NAME)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "branchName": "feature/test",
                                          "sourceBranch": "main"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());

        verify(repositoryManager).createBranch("feature/test", "main", repo);
    }

    @Test
    void getFile_returnsNotFoundWhenRepoMissing() throws Exception {
        when(repositoryConfigurationService.findByProjectCodeAndName(PROJECT_CODE, REPO_NAME)).thenReturn(Optional.empty());

        mockMvc.perform(get("/repository-management/{projectCode}/{repositoryName}/file", PROJECT_CODE, REPO_NAME)
                        .param("branch", "main")
                        .param("fileName", "test.txt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void commitFile_returnsServerErrorOnIoException() throws Exception {
        Path gitRepo = initGitRepo();
        ModelRepository repo = repoWithPath(gitRepo);
        when(repositoryConfigurationService.findByProjectCodeAndName(PROJECT_CODE, REPO_NAME))
                .thenReturn(Optional.of(repo));
        when(repositoryConfigurationService.findByName(REPO_NAME)).thenReturn(Optional.of(repo));
        doThrow(new IOException("fail")).when(repositoryManager).commitFile(any(CommitFileRequest.class), eq(repo));

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());

        mockMvc.perform(
                        multipart("/repository-management/{projectCode}/{repositoryName}/commit-file", PROJECT_CODE, REPO_NAME)
                                .file(file)
                                .param("branch", "main")
                                .param("commitMessage", "msg")
                                .param("authorName", "a")
                                .param("authorEmail", "a@test.com")
                )
                .andExpect(status().isInternalServerError());
    }

    @Test
    void commitFile_returnsBadRequestOnInvalidInput() throws Exception {
        Path gitRepo = initGitRepo();
        ModelRepository repo = repoWithPath(gitRepo);
        when(repositoryConfigurationService.findByProjectCodeAndName(PROJECT_CODE, REPO_NAME))
                .thenReturn(Optional.of(repo));
        when(repositoryConfigurationService.findByName(REPO_NAME)).thenReturn(Optional.of(repo));
        doThrow(new IllegalArgumentException("invalid")).when(repositoryManager).commitFile(any(CommitFileRequest.class), eq(repo));

        mockMvc.perform(
                        multipart("/repository-management/{projectCode}/{repositoryName}/commit-file", PROJECT_CODE, REPO_NAME)
                                .param("branch", "main")
                                .param("commitMessage", "msg")
                                .param("authorName", "a")
                                .param("authorEmail", "a@test.com")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFiles_returnsServerErrorOnGitIssues() throws Exception {
        Path gitRepo = initGitRepo();
        ModelRepository repo = repoWithPath(gitRepo);
        when(repositoryConfigurationService.findByProjectCodeAndName(PROJECT_CODE, REPO_NAME))
                .thenReturn(Optional.of(repo));
        when(repositoryManager.listFiles(repo)).thenThrow(new org.eclipse.jgit.api.errors.GitAPIException("fail") {});

        mockMvc.perform(get("/repository-management/{projectCode}/{repositoryName}/files", PROJECT_CODE, REPO_NAME)
                        .param("branch", "main"))
                .andExpect(status().isInternalServerError());
    }

    private static ModelRepository repoWithPath(Path path) {
        ModelRepository repo = new ModelRepository();
        repo.setName(REPO_NAME);
        repo.setProjectCode(PROJECT_CODE);
        repo.setPath(path.toString());
        repo.setMainBranch("main");
        return repo;
    }

    private static Path initGitRepo() throws Exception {
        Path dir = Files.createTempDirectory("repo");
        Git.init().setDirectory(dir.toFile()).call();
        return dir;
    }
}
