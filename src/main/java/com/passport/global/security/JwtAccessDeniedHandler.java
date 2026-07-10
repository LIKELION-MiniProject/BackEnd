package com.passport.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passport.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 인증은 됐지만 권한이 없는 요청(Spring Security 레벨)에 대한 403 응답 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        SecurityErrorResponseWriter.write(response, objectMapper, ErrorCode.ACCESS_DENIED);
    }
}
