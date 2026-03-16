package com.github.accessreport.controller;

import com.github.accessreport.dto.AccessReportResponse;
import com.github.accessreport.service.AccessReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller exposing the GitHub access report endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccessReportController {

    private final AccessReportService accessReportService;

    /**
     * Generate and return the access report for a GitHub organization.
     *
     * <p>Results are cached for 5 minutes. Add {@code ?refresh=true} to force
     * a fresh fetch (cache eviction handled server-side via a separate endpoint).
     *
     * <p><b>Example:</b> {@code GET /api/v1/report?org=octocat}
     *
     * @param org the GitHub organization login name (required)
     * @return a JSON access report showing which users have access to which repositories
     */
    @GetMapping("/report")
    public ResponseEntity<AccessReportResponse> getAccessReport(
            @RequestParam("org") String org) {

        log.info("Access report requested for org: {}", org);

        String sanitizedOrg = org.trim();
        if (sanitizedOrg.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        AccessReportResponse report = accessReportService.generateReport(sanitizedOrg);
        return ResponseEntity.ok(report);
    }

    /**
     * Health-check style endpoint to verify the service is running.
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("GitHub Access Report Service is running.");
    }
}
