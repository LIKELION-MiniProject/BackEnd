package com.passport.requirement.domain;

import com.passport.requirement.domain.UserGraduationRequirement.GraduationExamType;

/**
 * DiagnosisService·DashboardService가 실제로 사용하는 유효 요건.
 * 유저가 저장한 UserGraduationRequirement가 있으면 그 학점 기준을, 없으면 학과 하드코딩 기준을 담는다.
 */
public record EffectiveRequirement(
        String deptCode,
        int totalCredit,
        int majorRequiredCredit,
        int majorElectiveCredit,
        int majorBasicCredit,
        int geRequiredCredit,
        int geElectiveCredit,
        int generalElectiveCredit,
        int majorTotalCredit,
        int liberalTotalCredit,
        int coreLiberalTargetCount,
        GraduationExamType graduationExam,
        boolean languageCertRequired,
        boolean volunteerCertRequired,
        boolean thesisCertRequired,
        double minGpa,
        /** 유저가 입력한 졸업 인증 5분야 대상/완료 표기. 유저 저장값이 없으면(하드코딩 폴백) null. */
        RequirementCertificationTargets certificationTargets
) {

    public static EffectiveRequirement fromHardcoded(GraduationRequirement requirement) {
        return new EffectiveRequirement(
                requirement.deptCode(),
                requirement.totalCredit(),
                requirement.majorRequiredCredit(),
                requirement.majorElectiveCredit(),
                0,
                requirement.geRequiredCredit(),
                requirement.geElectiveCredit(),
                requirement.generalElectiveCredit(),
                requirement.majorRequiredCredit() + requirement.majorElectiveCredit(),
                requirement.geRequiredCredit() + requirement.geElectiveCredit(),
                0,
                requirement.thesisCertRequired() ? GraduationExamType.THESIS : GraduationExamType.NONE,
                requirement.languageCertRequired(),
                requirement.volunteerCertRequired(),
                requirement.thesisCertRequired(),
                requirement.minGpa(),
                null   // 하드코딩 폴백에는 유저 5분야 입력이 없음
        );
    }

    /** 인증 필수 여부·최소 GPA는 유저 입력 스키마(사진1)에 없는 항목이라 학과 하드코딩 기준을 그대로 따른다. */
    public static EffectiveRequirement fromUser(UserGraduationRequirement user, GraduationRequirement fallback) {
        RequirementCredits credits = user.getCredits();
        long coreLiberalTargetCount = user.getCoreLiberal().stream().filter(CoreLiberalArea::isTarget).count();

        return new EffectiveRequirement(
                fallback.deptCode(),
                credits.getTotal(),
                credits.getMajorRequired(),
                credits.getMajorElective(),
                credits.getMajorBasic(),
                credits.getLiberalRequired(),
                credits.getLiberalElective(),
                credits.getGeneralElective(),
                credits.getMajorTotal(),
                credits.getLiberalTotal(),
                (int) coreLiberalTargetCount,
                user.getGraduationExam(),
                fallback.languageCertRequired(),
                fallback.volunteerCertRequired(),
                fallback.thesisCertRequired(),
                fallback.minGpa(),
                user.getCertification()   // 유저가 입력한 인증 5분야 대상/완료
        );
    }
}
