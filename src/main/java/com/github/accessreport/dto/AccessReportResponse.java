package com.github.accessreport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Top-level API response for the access report endpoint.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccessReportResponse {

    private String organization;
    private Instant generatedAt;
    private ReportSummary summary;

    /**
     * User-centric view: maps each user to the list of repos they can access.
     */
    private Map<String, UserAccessDetail> userAccessMap;

    /**
     * Repository-centric view: maps each repo to the list of users with access.
     */
    private Map<String, List<RepoCollaboratorDetail>> repoAccessMap;

    @Data
    @Builder
    public static class ReportSummary {
        private int totalRepositories;
        private int totalUsers;
        private int totalAccessEntries;
    }

    @Data
    @Builder
    public static class UserAccessDetail {
        private String username;
        private String profileUrl;
        private String userType;
        private List<RepositoryAccess> repositories;
    }

    @Data
    @Builder
    public static class RepositoryAccess {
        private String repositoryName;
        private String fullName;
        private String visibility;
        private String permissionLevel;
        private String repositoryUrl;
    }

    @Data
    @Builder
    public static class RepoCollaboratorDetail {
        private String username;
        private String profileUrl;
        private String permissionLevel;
        private String userType;
    }
}
