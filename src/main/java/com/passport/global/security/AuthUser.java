package com.passport.global.security;

/** JWT 인증 성공 시 SecurityContext에 담기는 인증 주체(principal). */
public record AuthUser(Long id, String email) {
}
