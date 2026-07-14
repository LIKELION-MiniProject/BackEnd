package com.passport.requirement.service;

import com.passport.profile.domain.Profile;
import com.passport.requirement.domain.EffectiveRequirement;
import com.passport.requirement.domain.GraduationRequirement;
import com.passport.requirement.repository.UserRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 진단·대시보드가 사용할 "실제 적용 요건"을 결정한다: 유저 저장값 우선, 없으면 학과 하드코딩 폴백. */
@Service
@RequiredArgsConstructor
public class RequirementResolutionService {

    private final UserRequirementRepository userRequirementRepository;
    private final RequirementService requirementService;

    public EffectiveRequirement resolve(Profile profile) {
        GraduationRequirement fallback = requirementService.getByDeptCode(profile.getDeptCode());
        return userRequirementRepository.findByProfileId(profile.getId())
                .map(user -> EffectiveRequirement.fromUser(user, fallback))
                .orElseGet(() -> EffectiveRequirement.fromHardcoded(fallback));
    }
}
