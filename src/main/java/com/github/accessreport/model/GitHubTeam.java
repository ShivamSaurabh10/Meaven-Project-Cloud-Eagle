package com.github.accessreport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubTeam {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private String privacy;
    private String permission;

    @JsonProperty("members_url")
    private String membersUrl;

    @JsonProperty("repositories_url")
    private String repositoriesUrl;
}
