package com.passport.diagnosis.controller;

import com.passport.diagnosis.dto.DiagnosisResponse;
import com.passport.diagnosis.service.DiagnosisService;
import com.passport.global.common.ApiResponse;
import com.passport.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/diagnosis")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    @GetMapping
    public ApiResponse<DiagnosisResponse> diagnose(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long profileId) {
        return ApiResponse.success(diagnosisService.diagnose(profileId, authUser.id()));
    }
}
