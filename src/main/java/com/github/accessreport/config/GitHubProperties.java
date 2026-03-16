package com.github.accessreport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "github.api")
public class GitHubProperties {

    @NotBlank(message = "GitHub API token must be configured via GITHUB_TOKEN environment variable")
    private String token;

    private String baseUrl = "https://api.github.com";

    @Positive
    private int concurrency = 10;

    @Positive
    private int rateLimitPerHour = 5000;
}
