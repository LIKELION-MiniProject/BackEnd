package com.passport.certification.service;

import com.passport.certification.domain.Certification;
import com.passport.certification.dto.CertificationResponse;
import com.passport.certification.dto.CertificationUpdateRequest;
import com.passport.certification.repository.CertificationRepository;
import com.passport.profile.domain.Profile;
import com.passport.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificationService {

    private final CertificationRepository certificationRepository;
    private final ProfileService profileService;

    public List<CertificationResponse> list(Long profileId, Long userId) {
        profileService.findOwnedProfile(profileId, userId);
        return certificationRepository.findAllByProfileId(profileId).stream()
                .map(CertificationResponse::from)
                .toList();
    }

    @Transactional
    public List<CertificationResponse> update(Long profileId, Long userId, CertificationUpdateRequest request) {
        Profile profile = profileService.findOwnedProfile(profileId, userId);

        return request.items().stream()
                .map(item -> upsert(profile, item))
                .map(CertificationResponse::from)
                .toList();
    }

    private Certification upsert(Profile profile, CertificationUpdateRequest.Item item) {
        return certificationRepository.findByProfileIdAndType(profile.getId(), item.type())
                .map(certification -> {
                    certification.updateStatus(item.status());
                    return certification;
                })
                .orElseGet(() -> certificationRepository.save(
                        Certification.builder()
                                .profile(profile)
                                .type(item.type())
                                .status(item.status())
                                .build()
                ));
    }
}
