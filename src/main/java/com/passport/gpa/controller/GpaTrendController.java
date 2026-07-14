package com.passport.gpa.controller;

import com.passport.global.common.ApiResponse;
import com.passport.global.security.AuthUser;
import com.passport.gpa.dto.GpaTrendResponse;
import com.passport.gpa.service.GpaTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/gpa-trend")
@RequiredArgsConstructor
public class GpaTrendController {

    private final GpaTrendService gpaTrendService;

    @GetMapping
    public ApiResponse<GpaTrendResponse> trend(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long profileId) {
        return ApiResponse.success(gpaTrendService.getTrend(profileId, authUser.id()));
    }
}
