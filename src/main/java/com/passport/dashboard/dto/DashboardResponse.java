package com.passport.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.passport.diagnosis.dto.DiagnosisResponse;
import com.passport.gpa.dto.GpaTrendResponse;
import com.passport.recommendation.dto.PersonaDto;

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
        double requestedCredits,
        double earnedCredits,
        /** 최신 학기 GPA. 기존 최상위 gpa(GpaProgress 객체)와 이름이 겹쳐 semesterGpa로 명명 — ⚠️ FE 확인 필요 */
        Double semesterGpa,
        /** 최신 학기 수강목록 */
        List<CourseLine> courses,

        /**
         * additive(원석 요청): 학습 성향 persona 블록. 수강 이력이 바뀔 때마다 PersonaService가 재계산해
         * 저장해둔 값을 그대로 서빙(이 화면에서 재계산하지 않음). 수강 이력이 하나도 없으면 null.
         */
        PersonaDto persona
) {

    public record CategoryView(
            String key,
            String name,
            /** 학점 항목은 0.5 단위가 있어 소수(예: 39.5). 건수·영역수 항목도 같은 타입으로 내려간다(3.0 → JS에선 3). */
            Double current,
            /** 요건 기준값·건수는 항상 정수. 판정 근거가 없으면 null. */
            Integer required,
            @JsonInclude(JsonInclude.Include.NON_NULL) String unit,
            String status
    ) {
    }

    public record CourseLine(String category, String courseName, double credit, String grade) {
    }
}
