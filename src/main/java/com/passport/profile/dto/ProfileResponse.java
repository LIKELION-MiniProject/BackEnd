package com.passport.profile.dto;

import com.passport.profile.domain.Profile;

public record ProfileResponse(
        Long id,
        Long userId,
        String deptCode,
        String studentId,
        int admissionYear,
        String name
) {

    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getDeptCode(),
                profile.getStudentId(),
                profile.getAdmissionYear(),
                profile.getName()
        );
    }
}
