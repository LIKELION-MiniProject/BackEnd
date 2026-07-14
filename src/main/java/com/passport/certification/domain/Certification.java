package com.passport.certification.domain;

import com.passport.profile.domain.Profile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "certifications", uniqueConstraints = @UniqueConstraint(columnNames = {"profile_id", "type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificationStatus status;

    @Builder
    public Certification(Profile profile, CertificationType type, CertificationStatus status) {
        this.profile = profile;
        this.type = type;
        this.status = status;
    }

    public void updateStatus(CertificationStatus status) {
        this.status = status;
    }

    public enum CertificationType {
        LANGUAGE, VOLUNTEER, THESIS
    }

    public enum CertificationStatus {
        PASS, FAIL, NOT_SUBMITTED
    }
}
