package com.passport.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passport.global.error.ErrorCode;
import com.passport.global.error.GlobalExceptionHandler.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/** Security 필터 단계(컨트롤러 진입 전)에서 거부된 요청을 GlobalExceptionHandler와 동일한 JSON 포맷으로 응답. */
final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}
