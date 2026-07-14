package com.passport.auth.controller;

import com.passport.auth.dto.LoginRequest;
import com.passport.auth.dto.LoginResponse;
import com.passport.auth.dto.MeResponse;
import com.passport.auth.dto.SignupRequest;
import com.passport.auth.dto.SignupResponse;
import com.passport.auth.service.AuthService;
import com.passport.global.common.ApiResponse;
import com.passport.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // MVP 범위: 서버는 세션/블랙리스트를 두지 않음 — 클라이언트가 토큰을 버리면 로그아웃 완료
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(authService.me(authUser.id()));
    }
}
