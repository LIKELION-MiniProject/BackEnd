package com.passport.profile.dto;

public record ProfileCreateRequest(
        String deptCode,
        String studentId,
        int admissionYear,
        String name
) {
}
