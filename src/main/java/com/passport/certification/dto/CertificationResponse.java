package com.passport.certification.dto;

import com.passport.certification.domain.Certification;

public record CertificationResponse(
        Certification.CertificationType type,
        Certification.CertificationStatus status
) {

    public static CertificationResponse from(Certification certification) {
        return new CertificationResponse(certification.getType(), certification.getStatus());
    }
}
