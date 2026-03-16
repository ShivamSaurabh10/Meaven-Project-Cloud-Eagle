package com.github.accessreport.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoint to manually evict cached reports.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class CacheController {

    private final CacheManager cacheManager;

    /**
     * Evict the cached report for a specific organization, forcing a fresh fetch on next call.
     *
     * <p><b>Example:</b> {@code DELETE /api/v1/admin/cache?org=octocat}
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, String>> evictCache(@RequestParam("org") String org) {
        var cache = cacheManager.getCache("accessReports");
        if (cache != null) {
            cache.evict(org.trim());
        }
        return ResponseEntity.ok(Map.of(
                "message", "Cache evicted for organization: " + org,
                "organization", org
        ));
    }

    /**
     * Evict all cached reports.
     */
    @DeleteMapping("/cache/all")
    public ResponseEntity<Map<String, String>> evictAllCaches() {
        var cache = cacheManager.getCache("accessReports");
        if (cache != null) {
            cache.clear();
        }
        return ResponseEntity.ok(Map.of("message", "All access report caches cleared."));
    }
}
