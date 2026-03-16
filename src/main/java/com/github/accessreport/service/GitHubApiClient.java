package com.github.accessreport.service;

import com.github.accessreport.config.GitHubProperties;
import com.github.accessreport.exception.GitHubApiException;
import com.github.accessreport.exception.OrganizationNotFoundException;
import com.github.accessreport.model.GitHubCollaborator;
import com.github.accessreport.model.GitHubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Low-level GitHub API client.
 * Handles pagination automatically using GitHub's Link header.
 * All requests are non-blocking using Project Reactor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubApiClient {

    private static final int PAGE_SIZE = 100; // Max allowed by GitHub API
    private static final Pattern NEXT_PAGE_PATTERN =
            Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    private final WebClient gitHubWebClient;
    private final GitHubProperties gitHubProperties;

    /**
     * Fetches all repositories for the given organization, handling pagination.
     */
    public Flux<GitHubRepository> fetchAllRepositories(String orgName) {
        log.info("Fetching repositories for organization: {}", orgName);
        String initialUrl = "/orgs/{org}/repos?type=all&per_page=" + PAGE_SIZE;
        return fetchAllPages(initialUrl, GitHubRepository.class, orgName)
                .onErrorMap(WebClientResponseException.NotFound.class,
                        ex -> new OrganizationNotFoundException(orgName));
    }

    /**
     * Fetches all collaborators for a specific repository with their permissions.
     */
    public Flux<GitHubCollaborator> fetchRepositoryCollaborators(String orgName, String repoName) {
        log.debug("Fetching collaborators for repo: {}/{}", orgName, repoName);
        String initialUrl = "/repos/{org}/{repo}/collaborators?affiliation=all&per_page=" + PAGE_SIZE;
        return fetchAllPages(initialUrl, GitHubCollaborator.class, orgName, repoName)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    // Some repos may not be accessible; log and continue
                    log.warn("Could not fetch collaborators for {}/{}: HTTP {}",
                            orgName, repoName, ex.getStatusCode().value());
                    return Flux.empty();
                });
    }

    /**
     * Generic paginator that follows GitHub's Link header to fetch all pages.
     * Recursively fetches next pages until no more "next" link is present.
     */
    private <T> Flux<T> fetchAllPages(String urlTemplate, Class<T> type, Object... uriVars) {
        return fetchPage(urlTemplate, type, uriVars)
                .expand(page -> {
                    if (page.nextUrl() != null) {
                        return fetchPage(page.nextUrl(), type);
                    }
                    return Mono.empty();
                })
                .flatMap(page -> Flux.fromIterable(page.items()));
    }

    private <T> Mono<Page<T>> fetchPage(String url, Class<T> type, Object... uriVars) {
        return gitHubWebClient.get()
                .uri(url, uriVars)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(body -> {
                            int code = response.statusCode().value();
                            String message = "GitHub API error %d for URL %s: %s".formatted(code, url, body);
                            return Mono.error(new GitHubApiException(message, code));
                        }))
                .toEntityList(type)
                .map(entity -> {
                    List<T> items = entity.getBody() != null ? entity.getBody() : new ArrayList<>();
                    String linkHeader = entity.getHeaders().getFirst("Link");
                    String nextUrl = extractNextUrl(linkHeader);
                    return new Page<>(items, nextUrl);
                });
    }

    /**
     * Parses GitHub's Link response header to extract the "next" page URL.
     * Example: <https://api.github.com/orgs/octocat/repos?page=2>; rel="next"
     */
    private String extractNextUrl(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) {
            return null;
        }
        Matcher matcher = NEXT_PAGE_PATTERN.matcher(linkHeader);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Internal record to hold a single page of results and the URL to the next page.
     */
    private record Page<T>(List<T> items, String nextUrl) {}
}
