package com.passport.requirement.controller;

import com.passport.global.common.ApiResponse;
import com.passport.global.security.AuthUser;
import com.passport.requirement.dto.RequirementSaveRequest;
import com.passport.requirement.dto.UserRequirementResponse;
import com.passport.requirement.service.UserRequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/requirements")
@RequiredArgsConstructor
public class UserRequirementController {

    private final UserRequirementService userRequirementService;

    @GetMapping
    public ApiResponse<UserRequirementResponse> get(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long profileId) {
        return ApiResponse.success(userRequirementService.get(profileId, authUser.id()));
    }

    @PutMapping
    public ApiResponse<UserRequirementResponse> save(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long profileId,
                                                      @RequestBody RequirementSaveRequest request) {
        return ApiResponse.success(userRequirementService.save(profileId, authUser.id(), request));
    }
}
