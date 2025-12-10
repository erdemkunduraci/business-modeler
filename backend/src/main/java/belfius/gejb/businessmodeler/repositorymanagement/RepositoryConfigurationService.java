package belfius.gejb.businessmodeler.repositorymanagement;

import belfius.gejb.businessmodeler.model.ModelRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Binds repository definitions from application properties and exposes lookup by repository name.
 *
 * Example configuration:
 * model.repositories[0].name=sample
 * model.repositories[0].path=/repos/sample
 * model.repositories[0].type=git
 */
@Service
@ConfigurationProperties(prefix = "model")
public class RepositoryConfigurationService {

    private List<ModelRepository> repositories = new ArrayList<>();

    public List<ModelRepository> getRepositories() {
        return Collections.unmodifiableList(repositories);
    }

    public void setRepositories(List<ModelRepository> repositories) {
        this.repositories = repositories == null ? new ArrayList<>() : repositories;
    }

    /**
     * Find a repository configuration by its configured name.
     *
     * @param repositoryName logical repository name from configuration
     * @return matching repository or empty when not found
     */
    public Optional<ModelRepository> findByName(String repositoryName) {
        return repositories.stream()
                .filter(r -> repositoryName != null && repositoryName.equalsIgnoreCase(r.getName()))
                .findFirst();
    }

    public Optional<ModelRepository> findByProjectCodeAndName(String projectCode, String repositoryName) {
        return repositories.stream()
                .filter(r -> repositoryName != null && repositoryName.equalsIgnoreCase(r.getName()))
                .filter(r -> projectCode != null && projectCode.equalsIgnoreCase(r.getProjectCode()))
                .findFirst();
    }
}
