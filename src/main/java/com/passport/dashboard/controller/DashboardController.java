package com.passport.dashboard.controller;

import com.passport.dashboard.dto.DashboardResponse;
import com.passport.dashboard.service.DashboardService;
import com.passport.global.common.ApiResponse;
import com.passport.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardResponse> dashboard(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long profileId) {
        return ApiResponse.success(dashboardService.getDashboard(profileId, authUser.id()));
    }
}
