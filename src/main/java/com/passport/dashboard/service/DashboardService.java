package com.passport.dashboard.service;

import com.passport.dashboard.dto.DashboardResponse;
import com.passport.diagnosis.dto.DiagnosisResponse;
import com.passport.diagnosis.service.DiagnosisService;
import com.passport.gpa.dto.GpaTrendResponse;
import com.passport.gpa.service.GpaTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final DiagnosisService diagnosisService;
    private final GpaTrendService gpaTrendService;

    public DashboardResponse getDashboard(Long profileId, Long userId) {
        DiagnosisResponse diagnosis = diagnosisService.diagnose(profileId, userId);
        GpaTrendResponse gpaTrend = gpaTrendService.getTrend(profileId, userId);

        var shortfalls = diagnosis.categories().stream()
                .filter(category -> category.shortfall() > 0)
                .toList();

        return new DashboardResponse(
                profileId,
                diagnosis.deptCode(),
                diagnosis.eligibleForGraduation(),
                diagnosis.totalCredit(),
                shortfalls,
                diagnosis.certifications(),
                diagnosis.gpa(),
                gpaTrend.semesters()
        );
    }
}
