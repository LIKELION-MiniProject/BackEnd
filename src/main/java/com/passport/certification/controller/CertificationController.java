package com.passport.certification.controller;

import com.passport.certification.dto.CertificationResponse;
import com.passport.certification.dto.CertificationUpdateRequest;
import com.passport.certification.service.CertificationService;
import com.passport.global.common.ApiResponse;
import com.passport.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/certifications")
@RequiredArgsConstructor
public class CertificationController {

    private final CertificationService certificationService;

    @GetMapping
    public ApiResponse<List<CertificationResponse>> list(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long profileId) {
        return ApiResponse.success(certificationService.list(profileId, authUser.id()));
    }

    @PutMapping
    public ApiResponse<List<CertificationResponse>> update(@AuthenticationPrincipal AuthUser authUser,
                                                             @PathVariable Long profileId,
                                                             @RequestBody CertificationUpdateRequest request) {
        return ApiResponse.success(certificationService.update(profileId, authUser.id(), request));
    }
}
