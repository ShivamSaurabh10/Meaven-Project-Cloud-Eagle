package com.github.accessreport.controller;

import com.github.accessreport.dto.AccessReportResponse;
import com.github.accessreport.exception.OrganizationNotFoundException;
import com.github.accessreport.service.AccessReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccessReportController.class)
class AccessReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccessReportService accessReportService;

    @Test
    void getAccessReport_validOrg_returns200() throws Exception {
        AccessReportResponse report = AccessReportResponse.builder()
                .organization("octocat")
                .generatedAt(Instant.now())
                .summary(AccessReportResponse.ReportSummary.builder()
                        .totalRepositories(1)
                        .totalUsers(1)
                        .totalAccessEntries(1)
                        .build())
                .userAccessMap(Collections.emptyMap())
                .repoAccessMap(Collections.emptyMap())
                .build();

        when(accessReportService.generateReport("octocat")).thenReturn(report);

        mockMvc.perform(get("/api/v1/report").param("org", "octocat"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.organization").value("octocat"))
                .andExpect(jsonPath("$.summary.totalRepositories").value(1));
    }

    @Test
    void getAccessReport_missingOrgParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/report"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAccessReport_orgNotFound_returns404() throws Exception {
        when(accessReportService.generateReport("nonexistent"))
                .thenThrow(new OrganizationNotFoundException("nonexistent"));

        mockMvc.perform(get("/api/v1/report").param("org", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void ping_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk());
    }
}
