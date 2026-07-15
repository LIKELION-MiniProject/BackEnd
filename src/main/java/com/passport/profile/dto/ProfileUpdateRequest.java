package com.passport.profile.dto;

import com.passport.profile.domain.Profile.DoubleMajorType;
import com.passport.profile.domain.Profile.EnrollmentStatus;

public record ProfileUpdateRequest(
        String deptCode,
        String studentId,
        int admissionYear,
        String name,
        /** 마이페이지 표시 전용(PHASE1-①) — 전부 선택 입력, 안 보내면 null로 저장 */
        Integer grade,
        Integer currentSemester,
        EnrollmentStatus enrollmentStatus,
        Integer expectedGraduationYear,
        DoubleMajorType doubleMajorType,
        String additionalMajor,
        String advisorProfessor
) {
}
