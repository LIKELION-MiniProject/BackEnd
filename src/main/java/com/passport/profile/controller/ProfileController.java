package com.passport.profile.controller;

import com.passport.global.common.ApiResponse;
import com.passport.global.security.AuthUser;
import com.passport.profile.dto.ProfileCreateRequest;
import com.passport.profile.dto.ProfileResponse;
import com.passport.profile.dto.ProfileUpdateRequest;
import com.passport.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProfileResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                @RequestBody ProfileCreateRequest request) {
        return ApiResponse.success(profileService.create(authUser.id(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProfileResponse> get(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return ApiResponse.success(profileService.get(id, authUser.id()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProfileResponse> update(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
                                                @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.success(profileService.update(id, authUser.id(), request));
    }
}
