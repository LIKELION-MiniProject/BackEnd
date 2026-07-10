package com.passport.auth.dto;

public record LoginResponse(String accessToken, String tokenType, Long userId, String nickname) {

    public static LoginResponse of(String accessToken, Long userId, String nickname) {
        return new LoginResponse(accessToken, "Bearer", userId, nickname);
    }
}
