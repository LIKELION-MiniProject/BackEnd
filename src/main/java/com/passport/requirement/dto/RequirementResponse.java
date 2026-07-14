package com.passport.requirement.dto;

import com.passport.requirement.domain.GraduationRequirement;

public record RequirementResponse(
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

    public static RequirementResponse from(GraduationRequirement requirement) {
        return new RequirementResponse(
                requirement.deptCode(),
                requirement.totalCredit(),
                requirement.majorRequiredCredit(),
                requirement.majorElectiveCredit(),
                requirement.geRequiredCredit(),
                requirement.geElectiveCredit(),
                requirement.generalElectiveCredit(),
                requirement.languageCertRequired(),
                requirement.volunteerCertRequired(),
                requirement.thesisCertRequired(),
                requirement.minGpa()
        );
    }
}
