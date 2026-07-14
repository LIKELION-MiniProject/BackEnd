package com.passport.requirement.service;

import com.passport.global.error.BusinessException;
import com.passport.global.error.ErrorCode;
import com.passport.profile.domain.Profile;
import com.passport.profile.service.ProfileService;
import com.passport.requirement.domain.CoreLiberalArea;
import com.passport.requirement.domain.RequirementCertificationTargets;
import com.passport.requirement.domain.RequirementCredits;
import com.passport.requirement.domain.UserGraduationRequirement;
import com.passport.requirement.dto.RequirementSaveRequest;
import com.passport.requirement.dto.UserRequirementResponse;
import com.passport.requirement.repository.UserRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRequirementService {

    private final UserRequirementRepository userRequirementRepository;
    private final ProfileService profileService;
    private final RequirementService requirementService;

    public UserRequirementResponse get(Long profileId, Long userId) {
        Profile profile = profileService.findOwnedProfile(profileId, userId);
        return userRequirementRepository.findByProfileId(profileId)
                .map(UserRequirementResponse::fromUser)
                .orElseGet(() -> UserRequirementResponse.fromDefault(requirementService.getByDeptCode(profile.getDeptCode())));
    }

    @Transactional
    public UserRequirementResponse save(Long profileId, Long userId, RequirementSaveRequest request) {
        Profile profile = profileService.findOwnedProfile(profileId, userId);
        if (!request.draft()) {
            validateComplete(request);
        }

        RequirementCredits credits = request.credits() == null ? RequirementCredits.builder().build() : request.credits().toEntity();
        List<CoreLiberalArea> coreLiberal = toCoreLiberalEntities(request);
        RequirementCertificationTargets certification = request.certification() == null
                ? RequirementCertificationTargets.builder().build()
                : request.certification().toEntity();

        UserGraduationRequirement entity = userRequirementRepository.findByProfileId(profileId).orElse(null);
        if (entity == null) {
            entity = userRequirementRepository.save(
                    UserGraduationRequirement.builder()
                            .profile(profile)
                            .credits(credits)
                            .requiredCourseType(request.requiredCourseType())
                            .coreLiberal(coreLiberal)
                            .certification(certification)
                            .graduationExam(request.graduationExam())
                            .draft(request.draft())
                            .build()
            );
        } else {
            entity.update(credits, request.requiredCourseType(), coreLiberal, certification, request.graduationExam(), request.draft());
        }

        return UserRequirementResponse.fromUser(entity);
    }

    private List<CoreLiberalArea> toCoreLiberalEntities(RequirementSaveRequest request) {
        if (request.coreLiberal() == null) {
            return List.of();
        }
        return request.coreLiberal().stream().map(RequirementSaveRequest.CoreLiberalItem::toEntity).toList();
    }

    private void validateComplete(RequirementSaveRequest request) {
        if (request.credits() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "학점 항목(credits)은 필수입니다.");
        }
        if (request.coreLiberal() == null || request.coreLiberal().size() != 5) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "핵심교양은 5개 영역을 모두 입력해야 합니다.");
        }
        if (request.requiredCourseType() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "필수 이수구분(requiredCourseType)은 필수입니다.");
        }
        if (request.certification() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "졸업 인증 항목(certification)은 필수입니다.");
        }
        if (request.graduationExam() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "졸업시험/논문 항목(graduationExam)은 필수입니다.");
        }
    }
}
