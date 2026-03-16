package com.github.accessreport.service;

import com.github.accessreport.config.GitHubProperties;
import com.github.accessreport.dto.AccessReportResponse;
import com.github.accessreport.model.GitHubCollaborator;
import com.github.accessreport.model.GitHubRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessReportServiceTest {

    @Mock
    private GitHubApiClient apiClient;

    @Mock
    private GitHubProperties gitHubProperties;

    @InjectMocks
    private AccessReportService service;

    private static final String ORG = "test-org";

    @BeforeEach
    void setup() {
        when(gitHubProperties.getConcurrency()).thenReturn(5);
    }

    @Test
    void generateReport_withRepositoriesAndCollaborators_buildsCorrectReport() {
        // Arrange
        GitHubRepository repo1 = buildRepo(1L, "repo-alpha", "test-org/repo-alpha", "public");
        GitHubRepository repo2 = buildRepo(2L, "repo-beta", "test-org/repo-beta", "private");

        GitHubCollaborator alice = buildCollaborator("alice", "User", true, false, false);
        GitHubCollaborator bob = buildCollaborator("bob", "User", false, false, true);

        when(apiClient.fetchAllRepositories(ORG)).thenReturn(Flux.just(repo1, repo2));
        when(apiClient.fetchRepositoryCollaborators(ORG, "repo-alpha")).thenReturn(Flux.just(alice, bob));
        when(apiClient.fetchRepositoryCollaborators(ORG, "repo-beta")).thenReturn(Flux.just(alice));

        // Act
        AccessReportResponse report = service.generateReport(ORG);

        // Assert
        assertThat(report.getOrganization()).isEqualTo(ORG);
        assertThat(report.getSummary().getTotalRepositories()).isEqualTo(2);
        assertThat(report.getSummary().getTotalUsers()).isEqualTo(2);
        assertThat(report.getSummary().getTotalAccessEntries()).isEqualTo(3); // alice:2, bob:1

        // User-centric map
        assertThat(report.getUserAccessMap()).containsKeys("alice", "bob");
        assertThat(report.getUserAccessMap().get("alice").getRepositories()).hasSize(2);
        assertThat(report.getUserAccessMap().get("bob").getRepositories()).hasSize(1);

        // Repo-centric map
        assertThat(report.getRepoAccessMap()).containsKeys("test-org/repo-alpha", "test-org/repo-beta");
        assertThat(report.getRepoAccessMap().get("test-org/repo-alpha")).hasSize(2);
        assertThat(report.getRepoAccessMap().get("test-org/repo-beta")).hasSize(1);
    }

    @Test
    void generateReport_withNoRepositories_returnsEmptyReport() {
        when(apiClient.fetchAllRepositories(ORG)).thenReturn(Flux.empty());

        AccessReportResponse report = service.generateReport(ORG);

        assertThat(report.getSummary().getTotalRepositories()).isZero();
        assertThat(report.getSummary().getTotalUsers()).isZero();
        assertThat(report.getUserAccessMap()).isEmpty();
        assertThat(report.getRepoAccessMap()).isEmpty();
    }

    @Test
    void generateReport_permissionLevels_resolvedCorrectly() {
        GitHubRepository repo = buildRepo(1L, "repo", "test-org/repo", "private");
        GitHubCollaborator admin = buildCollaborator("adminUser", "User", true, false, false);
        GitHubCollaborator writer = buildCollaborator("writerUser", "User", false, false, true);
        GitHubCollaborator reader = buildCollaborator("readerUser", "User", false, false, false);

        when(apiClient.fetchAllRepositories(ORG)).thenReturn(Flux.just(repo));
        when(apiClient.fetchRepositoryCollaborators(ORG, "repo"))
                .thenReturn(Flux.just(admin, writer, reader));

        AccessReportResponse report = service.generateReport(ORG);

        var repoCollabs = report.getRepoAccessMap().get("test-org/repo");
        assertThat(repoCollabs).hasSize(3);

        var adminDetail = repoCollabs.stream()
                .filter(c -> c.getUsername().equals("adminUser")).findFirst().orElseThrow();
        assertThat(adminDetail.getPermissionLevel()).isEqualTo("admin");

        var writerDetail = repoCollabs.stream()
                .filter(c -> c.getUsername().equals("writerUser")).findFirst().orElseThrow();
        assertThat(writerDetail.getPermissionLevel()).isEqualTo("write");
    }

    // --- Helpers ---

    private GitHubRepository buildRepo(Long id, String name, String fullName, String visibility) {
        GitHubRepository repo = new GitHubRepository();
        repo.setId(id);
        repo.setName(name);
        repo.setFullName(fullName);
        repo.setVisibility(visibility);
        repo.setHtmlUrl("https://github.com/" + fullName);
        return repo;
    }

    private GitHubCollaborator buildCollaborator(
            String login, String type, boolean admin, boolean maintain, boolean push) {
        GitHubCollaborator c = new GitHubCollaborator();
        c.setLogin(login);
        c.setType(type);
        c.setHtmlUrl("https://github.com/" + login);

        GitHubCollaborator.Permissions perms = new GitHubCollaborator.Permissions();
        perms.setAdmin(admin);
        perms.setMaintain(maintain);
        perms.setPush(push);
        perms.setPull(!admin && !maintain && !push); // reader by default
        c.setPermissions(perms);
        return c;
    }
}
