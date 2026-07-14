package com.passport.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passport.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 인증 없이(또는 잘못된 토큰으로) 보호된 API에 접근했을 때 401 응답 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        SecurityErrorResponseWriter.write(response, objectMapper, ErrorCode.UNAUTHENTICATED);
    }
}
