package com.passport.profile.service;

import com.passport.auth.domain.User;
import com.passport.auth.repository.UserRepository;
import com.passport.global.error.BusinessException;
import com.passport.global.error.ErrorCode;
import com.passport.profile.domain.Profile;
import com.passport.profile.dto.ProfileCreateRequest;
import com.passport.profile.dto.ProfileResponse;
import com.passport.profile.dto.ProfileUpdateRequest;
import com.passport.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProfileResponse create(Long userId, ProfileCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (profileRepository.existsByUserId(user.getId())) {
            throw new BusinessException(ErrorCode.PROFILE_ALREADY_EXISTS);
        }

        Profile profile = Profile.builder()
                .user(user)
                .deptCode(request.deptCode())
                .studentId(request.studentId())
                .admissionYear(request.admissionYear())
                .name(request.name())
                .build();

        return ProfileResponse.from(profileRepository.save(profile));
    }

    public ProfileResponse get(Long profileId, Long userId) {
        return ProfileResponse.from(findOwnedProfile(profileId, userId));
    }

    @Transactional
    public ProfileResponse update(Long profileId, Long userId, ProfileUpdateRequest request) {
        Profile profile = findOwnedProfile(profileId, userId);
        profile.update(request.deptCode(), request.studentId(), request.admissionYear(), request.name());
        return ProfileResponse.from(profile);
    }

    /** 프로필 존재 + 소유권(토큰의 User == Profile.user)을 함께 검증 */
    public Profile findOwnedProfile(Long profileId, Long userId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));

        if (!profile.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROFILE_ACCESS_DENIED);
        }

        return profile;
    }
}
