package com.passport.profile.dto;

import com.passport.profile.domain.Profile;
import com.passport.profile.domain.Profile.DoubleMajorType;
import com.passport.profile.domain.Profile.EnrollmentStatus;

public record ProfileResponse(
        Long id,
        Long userId,
        String deptCode,
        String studentId,
        int admissionYear,
        String name,
        /** 마이페이지 표시 전용(PHASE1-①) — 값이 없으면 null(FE가 "-"로 표시) */
        Integer grade,
        Integer currentSemester,
        EnrollmentStatus enrollmentStatus,
        Integer expectedGraduationYear,
        DoubleMajorType doubleMajorType,
        String additionalMajor,
        String advisorProfessor
) {

    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getDeptCode(),
                profile.getStudentId(),
                profile.getAdmissionYear(),
                profile.getName(),
                profile.getGrade(),
                profile.getCurrentSemester(),
                profile.getEnrollmentStatus(),
                profile.getExpectedGraduationYear(),
                profile.getDoubleMajorType(),
                profile.getAdditionalMajor(),
                profile.getAdvisorProfessor()
        );
    }
}
