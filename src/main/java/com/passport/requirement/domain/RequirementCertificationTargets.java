package com.passport.requirement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 졸업 인증 5분야 각각의 대상 여부(사진1 §4).
 * 임시저장(draft) 시 일부 항목만 채워질 수 있어 각 필드는 nullable로 둔다 — 최종 제출 검증은 서비스 계층에서 수행.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequirementCertificationTargets {

    @Enumerated(EnumType.STRING)
    private CertMark foreignLangCert;
    @Enumerated(EnumType.STRING)
    private CertMark infoProcessing;
    @Enumerated(EnumType.STRING)
    private CertMark cpr;
    @Enumerated(EnumType.STRING)
    private CertMark socialService;
    @Enumerated(EnumType.STRING)
    private CertMark foreignLangExtra;

    @Builder
    public RequirementCertificationTargets(CertMark foreignLangCert, CertMark infoProcessing, CertMark cpr,
                                            CertMark socialService, CertMark foreignLangExtra) {
        this.foreignLangCert = foreignLangCert;
        this.infoProcessing = infoProcessing;
        this.cpr = cpr;
        this.socialService = socialService;
        this.foreignLangExtra = foreignLangExtra;
    }
}
