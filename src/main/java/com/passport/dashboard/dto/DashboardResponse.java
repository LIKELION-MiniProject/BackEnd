package com.passport.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.passport.diagnosis.dto.DiagnosisResponse;
import com.passport.gpa.dto.GpaTrendResponse;

import java.util.List;

public record DashboardResponse(
        Long profileId,
        String deptCode,
        boolean eligibleForGraduation,
        DiagnosisResponse.CreditProgress totalCredit,
        /** 요건에 미달한 이수 구분만 추려낸 목록 — 홈 화면 "부족 항목" 표시용 */
        List<DiagnosisResponse.CategoryProgress> shortfallCategories,
        List<DiagnosisResponse.CertificationProgress> certifications,
        DiagnosisResponse.GpaProgress gpa,
        List<GpaTrendResponse.SemesterGpa> gpaTrend,

        // ↓ STEP D 추가 — 성적 대시보드(사진3) 화면용. 위 필드는 하위호환을 위해 그대로 둔다.
        /** 전체 이수율 %(0~100). EffectiveRequirement.totalCredit 대비 이수 학점 */
        int overallProgress,
        /** 화면의 요건별 통합 카테고리(총이수/전공/교양/핵심교양/필수과목/졸업인증/졸업시험) */
        List<CategoryView> categories,
        /** 최신 학기("YYYY-N학기"). 수강 이력이 전혀 없으면 null */
        String semester,
        int requestedCredits,
        int earnedCredits,
        /** 최신 학기 GPA. 기존 최상위 gpa(GpaProgress 객체)와 이름이 겹쳐 semesterGpa로 명명 — ⚠️ FE 확인 필요 */
        Double semesterGpa,
        /** 최신 학기 수강목록 */
        List<CourseLine> courses
) {

    public record CategoryView(
            String key,
            String name,
            Integer current,
            Integer required,
            @JsonInclude(JsonInclude.Include.NON_NULL) String unit,
            String status
    ) {
    }

    public record CourseLine(String category, String courseName, int credit, String grade) {
    }
}
