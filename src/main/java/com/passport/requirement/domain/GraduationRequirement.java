package com.passport.requirement.domain;

/**
 * 학과별 졸업 요건 기준. DB에 저장하지 않고 학과별로 하드코딩된 상수를 담는 값 객체.
 * (CLAUDE.md 4장: Requirement는 JPA 엔티티가 아니어도 됨)
 */
public record GraduationRequirement(
        String deptCode,
        int totalCredit,
        int majorRequiredCredit,
        int majorElectiveCredit,
        int geRequiredCredit,
        int geElectiveCredit,
        int generalElectiveCredit,
        boolean languageCertRequired,
        boolean volunteerCertRequired,
        boolean thesisCertRequired,
        double minGpa
) {
}
