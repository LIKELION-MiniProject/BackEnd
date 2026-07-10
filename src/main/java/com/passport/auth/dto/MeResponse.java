package com.passport.auth.dto;

/** profileId가 null이면 FE는 온보딩(프로필 등록) 화면으로, 있으면 대시보드로 분기 */
public record MeResponse(Long userId, String email, String nickname, Long profileId) {
}
