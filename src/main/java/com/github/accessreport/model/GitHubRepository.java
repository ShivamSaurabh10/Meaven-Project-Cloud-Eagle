package com.github.accessreport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepository {

    private Long id;
    private String name;

    @JsonProperty("full_name")
    private String fullName;

    private String description;

    @JsonProperty("private")
    private boolean isPrivate;

    @JsonProperty("html_url")
    private String htmlUrl;

    private String visibility;

    @JsonProperty("default_branch")
    private String defaultBranch;
}
