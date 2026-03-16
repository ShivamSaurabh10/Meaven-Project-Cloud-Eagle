package com.github.accessreport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubCollaborator {

    private Long id;
    private String login;
    private String name;
    private String email;
    private String type;

    @JsonProperty("site_admin")
    private boolean siteAdmin;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    // Permissions object present when fetching collaborators
    private Permissions permissions;

    @JsonProperty("role_name")
    private String roleName;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Permissions {
        private boolean admin;
        private boolean maintain;
        private boolean push;
        private boolean triage;
        private boolean pull;

        /**
         * Returns the highest permission level as a human-readable string.
         */
        public String highestLevel() {
            if (admin) return "admin";
            if (maintain) return "maintain";
            if (push) return "write";
            if (triage) return "triage";
            if (pull) return "read";
            return "none";
        }
    }
}
