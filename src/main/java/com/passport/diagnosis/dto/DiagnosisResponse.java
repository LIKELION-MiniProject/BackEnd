package com.passport.diagnosis.dto;

import com.passport.certification.domain.Certification.CertificationStatus;
import com.passport.certification.domain.Certification.CertificationType;
import com.passport.course.domain.Course.CourseCategory;

import java.util.List;

public record DiagnosisResponse(
        String deptCode,
        CreditProgress totalCredit,
        List<CategoryProgress> categories,
        List<CertificationProgress> certifications,
        GpaProgress gpa,
        boolean eligibleForGraduation
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

    /** current가 null이면 GPA 대상 과목이 아직 없다는 뜻(F/P/NP뿐이거나 이수 이력 없음) */
    public record GpaProgress(Double current, double required, boolean fulfilled) {
    }
}
