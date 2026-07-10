package com.passport.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON-001", "입력값이 올바르지 않습니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-002", "요청한 리소스를 찾을 수 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "COMMON-003", "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-004", "서버 내부 오류가 발생했습니다."),

    // Auth (다음 단계에서 사용)
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "AUTH-001", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "AUTH-003", "인증이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH-004", "회원을 찾을 수 없습니다."),

    // Profile
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE-001", "프로필을 찾을 수 없습니다."),
    PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "PROFILE-002", "이미 프로필이 등록되어 있습니다."),
    PROFILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PROFILE-003", "본인의 프로필만 접근할 수 있습니다."),

    // Course
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE-001", "수강 이력을 찾을 수 없습니다."),

    // Certification
    CERTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CERT-001", "졸업 인증 정보를 찾을 수 없습니다."),

    // Requirement
    REQUIREMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "REQ-001", "지원하지 않는 학과 코드입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
