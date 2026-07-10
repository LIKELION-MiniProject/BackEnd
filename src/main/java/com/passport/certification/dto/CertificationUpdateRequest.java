package com.passport.certification.dto;

import com.passport.certification.domain.Certification;

import java.util.List;

public record CertificationUpdateRequest(List<Item> items) {

    public record Item(Certification.CertificationType type, Certification.CertificationStatus status) {
    }
}
