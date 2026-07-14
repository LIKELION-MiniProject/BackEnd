package com.passport.diagnosis.dto;

import com.passport.certification.domain.Certification.CertificationStatus;
import com.passport.certification.domain.Certification.CertificationType;
import com.passport.course.domain.Course.CourseCategory;
import com.passport.requirement.domain.CertMark;

import java.util.List;

public record DiagnosisResponse(
        String deptCode,
        CreditProgress totalCredit,
        List<CategoryProgress> categories,
        List<CertificationProgress> certifications,
        GpaProgress gpa,
        boolean eligibleForGraduation,
        /** 유저 입력 졸업 인증 5분야 판정. 유저 요건 저장값이 없으면 null(기존 동작 유지). */
        GraduationCertificationProgress graduationCertification
) {

    public record CreditProgress(int earned, int required, int shortfall) {
        public static CreditProgress of(int earned, int required) {
            return new CreditProgress(earned, required, Math.max(0, required - earned));
        }
    }

    public record CategoryProgress(CourseCategory category, int earnedCredit, int requiredCredit, int shortfall) {
        public static CategoryProgress of(CourseCategory category, int earned, int required) {
            return new CategoryProgress(category, earned, required, Math.max(0, required - earned));
        }
    }

    public record CertificationProgress(CertificationType type, boolean required, CertificationStatus status, boolean fulfilled) {
    }

    /**
     * 유저 입력 졸업 인증 5분야 판정.
     * 규칙: 유저가 '대상(TARGET)'으로 표시한 분야는 전부 '완료(DONE)'여야 fulfilled=true.
     *       '비대상(NOT_TARGET)'은 무시. (대학 규칙을 창작하지 않고 유저 선언을 그대로 검증)
     */
    public record GraduationCertificationProgress(List<AreaMark> areas, boolean fulfilled) {
    }

    public record AreaMark(String area, CertMark mark) {
    }

    /** current가 null이면 GPA 대상 과목이 아직 없다는 뜻(F/P/NP뿐이거나 이수 이력 없음) */
    public record GpaProgress(Double current, double required, boolean fulfilled) {
    }
}
