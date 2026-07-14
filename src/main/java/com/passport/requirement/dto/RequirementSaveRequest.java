package com.passport.requirement.dto;

import com.passport.requirement.domain.CertMark;
import com.passport.requirement.domain.CoreLiberalArea;
import com.passport.requirement.domain.RequirementCertificationTargets;
import com.passport.requirement.domain.RequirementCredits;
import com.passport.requirement.domain.UserGraduationRequirement.GraduationExamType;
import com.passport.requirement.domain.UserGraduationRequirement.RequiredCourseType;

import java.util.List;

/** PUT /profiles/{id}/requirements 요청 본문. 키 이름은 CLAUDE.md 부록 PART 3 계약과 그대로 맞춘다. */
public record RequirementSaveRequest(
        Credits credits,
        RequiredCourseType requiredCourseType,
        List<CoreLiberalItem> coreLiberal,
        CertificationTargets certification,
        GraduationExamType graduationExam,
        boolean draft
) {

    public record Credits(
            int total, int majorRequired, int majorElective, int majorBasic,
            int liberalRequired, int liberalElective, int majorTotal, int liberalTotal,
            int teaching, int generalElective, int external, int doubleMajor,
            int minor, int linkedMajor, int convergenceMajor
    ) {
        public RequirementCredits toEntity() {
            return RequirementCredits.builder()
                    .total(total).majorRequired(majorRequired).majorElective(majorElective).majorBasic(majorBasic)
                    .liberalRequired(liberalRequired).liberalElective(liberalElective)
                    .majorTotal(majorTotal).liberalTotal(liberalTotal)
                    .teaching(teaching).generalElective(generalElective).external(external)
                    .doubleMajor(doubleMajor).minor(minor).linkedMajor(linkedMajor).convergenceMajor(convergenceMajor)
                    .build();
        }
    }

    public record CoreLiberalItem(int areaNo, String areaName, int courseCount, int credit, boolean target) {
        public CoreLiberalArea toEntity() {
            return CoreLiberalArea.builder()
                    .areaNo(areaNo).areaName(areaName).courseCount(courseCount).credit(credit).target(target)
                    .build();
        }
    }

    public record CertificationTargets(
            CertMark foreignLangCert, CertMark infoProcessing, CertMark cpr,
            CertMark socialService, CertMark foreignLangExtra
    ) {
        public RequirementCertificationTargets toEntity() {
            return RequirementCertificationTargets.builder()
                    .foreignLangCert(foreignLangCert).infoProcessing(infoProcessing).cpr(cpr)
                    .socialService(socialService).foreignLangExtra(foreignLangExtra)
                    .build();
        }
    }
}
