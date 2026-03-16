package com.github.accessreport.service;

import com.github.accessreport.config.GitHubProperties;
import com.github.accessreport.dto.AccessReportResponse;
import com.github.accessreport.dto.AccessReportResponse.*;
import com.github.accessreport.model.GitHubCollaborator;
import com.github.accessreport.model.GitHubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Orchestrates fetching repository and collaborator data from GitHub,
 * then builds the aggregated access report.
 *
 * <p><b>Scale Strategy:</b>
 * <ul>
 *   <li>Repositories are fetched once with full pagination.</li>
 *   <li>Collaborator fetches are run in parallel, capped by the configured
 *       concurrency limit (default 10) to respect GitHub rate limits.</li>
 *   <li>Results are cached in-memory for 5 minutes to avoid redundant API calls.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessReportService {

    private final GitHubApiClient apiClient;
    private final GitHubProperties gitHubProperties;

    /**
     * Generates a full access report for the given GitHub organization.
     * Results are cached for 5 minutes.
     *
     * @param orgName the GitHub organization login name
     * @return a structured access report
     */
    @Cacheable(value = "accessReports", key = "#orgName")
    public AccessReportResponse generateReport(String orgName) {
        log.info("Generating access report for organization: {}", orgName);
        long startTime = System.currentTimeMillis();

        // Step 1: Fetch all repositories (paginated)
        List<GitHubRepository> repositories = apiClient.fetchAllRepositories(orgName)
                .collectList()
                .block();

        if (repositories == null || repositories.isEmpty()) {
            log.info("No repositories found for organization: {}", orgName);
            return buildEmptyReport(orgName);
        }

        log.info("Found {} repositories in org '{}'. Fetching collaborators in parallel (concurrency={})...",
                repositories.size(), orgName, gitHubProperties.getConcurrency());

        // Step 2: Fetch collaborators for ALL repos in parallel, limited by concurrency
        // This avoids sequential calls and respects rate limits
        Map<String, List<GitHubCollaborator>> repoCollaborators = Flux.fromIterable(repositories)
                .flatMap(repo ->
                        apiClient.fetchRepositoryCollaborators(orgName, repo.getName())
                                .collectList()
                                .map(collaborators -> Tuples.of(repo, collaborators)),
                        gitHubProperties.getConcurrency()   // max concurrency parameter
                )
                .collectMap(
                        tuple -> tuple.getT1().getName(),
                        tuple -> tuple.getT2()
                )
                .block();

        if (repoCollaborators == null) {
            repoCollaborators = new HashMap<>();
        }

        // Step 3: Build the aggregated report
        AccessReportResponse report = buildReport(orgName, repositories, repoCollaborators);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Access report for '{}' generated in {}ms. Repos={}, Users={}",
                orgName, elapsed,
                report.getSummary().getTotalRepositories(),
                report.getSummary().getTotalUsers());

        return report;
    }

    private AccessReportResponse buildReport(
            String orgName,
            List<GitHubRepository> repositories,
            Map<String, List<GitHubCollaborator>> repoCollaborators) {

        // --- Build repo-centric map ---
        Map<String, List<RepoCollaboratorDetail>> repoAccessMap = new LinkedHashMap<>();

        for (GitHubRepository repo : repositories) {
            List<GitHubCollaborator> collaborators =
                    repoCollaborators.getOrDefault(repo.getName(), Collections.emptyList());

            List<RepoCollaboratorDetail> details = collaborators.stream()
                    .map(c -> RepoCollaboratorDetail.builder()
                            .username(c.getLogin())
                            .profileUrl(c.getHtmlUrl())
                            .permissionLevel(resolvePermission(c))
                            .userType(c.getType())
                            .build())
                    .sorted(Comparator.comparing(RepoCollaboratorDetail::getUsername))
                    .collect(Collectors.toList());

            repoAccessMap.put(repo.getFullName(), details);
        }

        // --- Build user-centric map ---
        // Aggregate all (user → repos) relationships in one pass
        Map<String, UserAccumulator> userAccumulatorMap = new ConcurrentHashMap<>();

        for (GitHubRepository repo : repositories) {
            List<GitHubCollaborator> collaborators =
                    repoCollaborators.getOrDefault(repo.getName(), Collections.emptyList());

            for (GitHubCollaborator collaborator : collaborators) {
                userAccumulatorMap
                        .computeIfAbsent(collaborator.getLogin(),
                                login -> new UserAccumulator(collaborator))
                        .addRepository(repo, resolvePermission(collaborator));
            }
        }

        Map<String, UserAccessDetail> userAccessMap = userAccumulatorMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().toUserAccessDetail(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        int totalAccessEntries = repoAccessMap.values().stream()
                .mapToInt(List::size)
                .sum();

        return AccessReportResponse.builder()
                .organization(orgName)
                .generatedAt(Instant.now())
                .summary(ReportSummary.builder()
                        .totalRepositories(repositories.size())
                        .totalUsers(userAccessMap.size())
                        .totalAccessEntries(totalAccessEntries)
                        .build())
                .userAccessMap(userAccessMap)
                .repoAccessMap(repoAccessMap)
                .build();
    }

    /**
     * Resolves the effective permission level from a collaborator's permissions object.
     * Falls back to roleName if permissions object is null (e.g., outside-collaborators endpoint).
     */
    private String resolvePermission(GitHubCollaborator collaborator) {
        if (collaborator.getPermissions() != null) {
            return collaborator.getPermissions().highestLevel();
        }
        return collaborator.getRoleName() != null ? collaborator.getRoleName() : "unknown";
    }

    private AccessReportResponse buildEmptyReport(String orgName) {
        return AccessReportResponse.builder()
                .organization(orgName)
                .generatedAt(Instant.now())
                .summary(ReportSummary.builder()
                        .totalRepositories(0)
                        .totalUsers(0)
                        .totalAccessEntries(0)
                        .build())
                .userAccessMap(Collections.emptyMap())
                .repoAccessMap(Collections.emptyMap())
                .build();
    }

    /**
     * Helper accumulator to build user → [repos] mapping efficiently.
     */
    private static class UserAccumulator {
        private final GitHubCollaborator collaborator;
        private final List<RepositoryAccess> repositories = new ArrayList<>();

        UserAccumulator(GitHubCollaborator collaborator) {
            this.collaborator = collaborator;
        }

        void addRepository(GitHubRepository repo, String permissionLevel) {
            repositories.add(RepositoryAccess.builder()
                    .repositoryName(repo.getName())
                    .fullName(repo.getFullName())
                    .visibility(repo.getVisibility())
                    .permissionLevel(permissionLevel)
                    .repositoryUrl(repo.getHtmlUrl())
                    .build());
        }

        UserAccessDetail toUserAccessDetail() {
            return UserAccessDetail.builder()
                    .username(collaborator.getLogin())
                    .profileUrl(collaborator.getHtmlUrl())
                    .userType(collaborator.getType())
                    .repositories(repositories.stream()
                            .sorted(Comparator.comparing(RepositoryAccess::getRepositoryName))
                            .collect(Collectors.toList()))
                    .build();
        }
    }
}
