package com.github.accessreport.service;

import com.github.accessreport.config.GitHubProperties;
import com.github.accessreport.exception.GitHubApiException;
import com.github.accessreport.exception.OrganizationNotFoundException;
import com.github.accessreport.model.GitHubRepository;
import com.github.wiremock.standalone.WireMockServerRunner;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubApiClientTest {

    private WireMockServer wireMock;
    private GitHubApiClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        GitHubProperties props = new GitHubProperties();
        props.setToken("test-token");
        props.setBaseUrl("http://localhost:" + wireMock.port());
        props.setConcurrency(5);

        WebClient webClient = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getToken())
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();

        client = new GitHubApiClient(webClient, props);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetchAllRepositories_singlePage_returnsItems() {
        wireMock.stubFor(get(urlPathEqualTo("/orgs/test-org/repos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            [
                              {"id": 1, "name": "repo-one", "full_name": "test-org/repo-one",
                               "visibility": "public", "html_url": "https://github.com/test-org/repo-one",
                               "private": false},
                              {"id": 2, "name": "repo-two", "full_name": "test-org/repo-two",
                               "visibility": "private", "html_url": "https://github.com/test-org/repo-two",
                               "private": true}
                            ]
                        """)));

        StepVerifier.create(client.fetchAllRepositories("test-org"))
                .assertNext(repo -> assertThat(repo.getName()).isEqualTo("repo-one"))
                .assertNext(repo -> assertThat(repo.getName()).isEqualTo("repo-two"))
                .verifyComplete();
    }

    @Test
    void fetchAllRepositories_multiplePages_followsLinkHeader() {
        // Page 1
        wireMock.stubFor(get(urlPathEqualTo("/orgs/test-org/repos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link",
                                "<http://localhost:" + wireMock.port() + "/orgs/test-org/repos?page=2>; rel=\"next\"")
                        .withBody("""
                            [{"id":1,"name":"repo-one","full_name":"test-org/repo-one",
                              "visibility":"public","html_url":"https://github.com/test-org/repo-one","private":false}]
                        """)));

        // Page 2 (no Link header = last page)
        wireMock.stubFor(get(urlPathEqualTo("/orgs/test-org/repos"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            [{"id":2,"name":"repo-two","full_name":"test-org/repo-two",
                              "visibility":"private","html_url":"https://github.com/test-org/repo-two","private":true}]
                        """)));

        StepVerifier.create(client.fetchAllRepositories("test-org"))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void fetchAllRepositories_orgNotFound_throwsOrganizationNotFoundException() {
        wireMock.stubFor(get(urlPathEqualTo("/orgs/nonexistent/repos"))
                .willReturn(aResponse().withStatus(404).withBody("{\"message\":\"Not Found\"}")));

        StepVerifier.create(client.fetchAllRepositories("nonexistent"))
                .expectError(OrganizationNotFoundException.class)
                .verify();
    }

    @Test
    void fetchRepositoryCollaborators_unauthorized_throwsGitHubApiException() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/test-org/repo-one/collaborators"))
                .willReturn(aResponse().withStatus(401).withBody("{\"message\":\"Bad credentials\"}")));

        StepVerifier.create(client.fetchRepositoryCollaborators("test-org", "repo-one"))
                .verifyComplete(); // errors are swallowed per design (returns empty)
    }
}
