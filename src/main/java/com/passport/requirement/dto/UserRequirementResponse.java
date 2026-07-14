package com.passport.requirement.dto;

import com.passport.requirement.domain.CertMark;
import com.passport.requirement.domain.GraduationRequirement;
import com.passport.requirement.domain.RequirementCertificationTargets;
import com.passport.requirement.domain.RequirementCredits;
import com.passport.requirement.domain.UserGraduationRequirement;
import com.passport.requirement.domain.UserGraduationRequirement.GraduationExamType;
import com.passport.requirement.domain.UserGraduationRequirement.RequiredCourseType;

import java.util.List;

/** GET/PUT /profiles/{id}/requirements 응답. source로 유저 저장값인지 학과 기본값인지 FE가 구분할 수 있게 한다. */
public record UserRequirementResponse(
        RequirementSaveRequest.Credits credits,
        RequiredCourseType requiredCourseType,
        List<RequirementSaveRequest.CoreLiberalItem> coreLiberal,
        RequirementSaveRequest.CertificationTargets certification,
        GraduationExamType graduationExam,
        boolean draft,
        String source
) {

    public static UserRequirementResponse fromUser(UserGraduationRequirement entity) {
        RequirementCredits c = entity.getCredits();
        RequirementCertificationTargets cert = entity.getCertification();

        return new UserRequirementResponse(
                new RequirementSaveRequest.Credits(
                        c.getTotal(), c.getMajorRequired(), c.getMajorElective(), c.getMajorBasic(),
                        c.getLiberalRequired(), c.getLiberalElective(), c.getMajorTotal(), c.getLiberalTotal(),
                        c.getTeaching(), c.getGeneralElective(), c.getExternal(),
                        c.getDoubleMajor(), c.getMinor(), c.getLinkedMajor(), c.getConvergenceMajor()
                ),
                entity.getRequiredCourseType(),
                entity.getCoreLiberal().stream()
                        .map(a -> new RequirementSaveRequest.CoreLiberalItem(a.getAreaNo(), a.getAreaName(), a.getCourseCount(), a.getCredit(), a.isTarget()))
                        .toList(),
                cert == null ? null : new RequirementSaveRequest.CertificationTargets(
                        cert.getForeignLangCert(), cert.getInfoProcessing(), cert.getCpr(), cert.getSocialService(), cert.getForeignLangExtra()
                ),
                entity.getGraduationExam(),
                entity.isDraft(),
                "user"
        );
    }

    /**
     * 저장된 유저 요건이 없을 때 보여줄 기본값.
     * ⚠️ 학과 하드코딩(BigdataAiRequirement)은 이 화면 스키마보다 단순해 전공기초/핵심교양 영역명/인증 5분야/졸업시험
     * 등은 대응되는 근거 데이터가 없다. 근거 없는 값을 지어내지 않고 0 / 빈 문자열 / NOT_TARGET / NONE으로 둔다 —
     * 실제 값은 팀이 확정하면 BigdataAiRequirement 교체만으로는 부족하고 이 매핑도 함께 보강해야 한다.
     */
    public static UserRequirementResponse fromDefault(GraduationRequirement requirement) {
        RequirementSaveRequest.Credits credits = new RequirementSaveRequest.Credits(
                requirement.totalCredit(),
                requirement.majorRequiredCredit(),
                requirement.majorElectiveCredit(),
                0,
                requirement.geRequiredCredit(),
                requirement.geElectiveCredit(),
                requirement.majorRequiredCredit() + requirement.majorElectiveCredit(),
                requirement.geRequiredCredit() + requirement.geElectiveCredit(),
                0,
                requirement.generalElectiveCredit(),
                0, 0, 0, 0, 0
        );
        List<RequirementSaveRequest.CoreLiberalItem> coreLiberal = List.of(
                new RequirementSaveRequest.CoreLiberalItem(1, null, 0, 0, false),
                new RequirementSaveRequest.CoreLiberalItem(2, null, 0, 0, false),
                new RequirementSaveRequest.CoreLiberalItem(3, null, 0, 0, false),
                new RequirementSaveRequest.CoreLiberalItem(4, null, 0, 0, false),
                new RequirementSaveRequest.CoreLiberalItem(5, null, 0, 0, false)
        );
        RequirementSaveRequest.CertificationTargets certification = new RequirementSaveRequest.CertificationTargets(
                requirement.languageCertRequired() ? CertMark.TARGET : CertMark.NOT_TARGET,
                CertMark.NOT_TARGET,
                CertMark.NOT_TARGET,
                requirement.volunteerCertRequired() ? CertMark.TARGET : CertMark.NOT_TARGET,
                CertMark.NOT_TARGET
        );
        GraduationExamType graduationExam = requirement.thesisCertRequired() ? GraduationExamType.THESIS : GraduationExamType.NONE;

        return new UserRequirementResponse(credits, null, coreLiberal, certification, graduationExam, false, "default");
    }
}
