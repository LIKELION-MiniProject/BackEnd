package com.passport.certification.repository;

import com.passport.certification.domain.Certification;
import com.passport.certification.domain.Certification.CertificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    List<Certification> findAllByProfileId(Long profileId);

    Optional<Certification> findByProfileIdAndType(Long profileId, CertificationType type);
}
