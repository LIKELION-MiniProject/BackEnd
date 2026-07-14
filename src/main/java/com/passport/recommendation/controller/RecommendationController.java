package com.passport.recommendation.controller;

import com.passport.global.common.ApiResponse;
import com.passport.global.security.AuthUser;
import com.passport.recommendation.dto.RecommendationResponse;
import com.passport.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping
    public ApiResponse<RecommendationResponse> generate(@AuthenticationPrincipal AuthUser authUser,
                                                          @PathVariable Long profileId) {
        return ApiResponse.success(recommendationService.generate(profileId, authUser.id()));
    }

    @GetMapping
    public ApiResponse<RecommendationResponse> get(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long profileId) {
        return ApiResponse.success(recommendationService.getRecommendations(profileId, authUser.id()));
    }
}
