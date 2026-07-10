package com.passport.dashboard.dto;

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
        List<GpaTrendResponse.SemesterGpa> gpaTrend
) {
}
