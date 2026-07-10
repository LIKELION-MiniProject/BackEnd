package com.passport.profile.dto;

public record ProfileUpdateRequest(
        String deptCode,
        String studentId,
        int admissionYear,
        String name
) {
}
