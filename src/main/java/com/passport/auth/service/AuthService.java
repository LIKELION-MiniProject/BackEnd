package com.passport.auth.service;

import com.passport.auth.domain.User;
import com.passport.auth.dto.LoginRequest;
import com.passport.auth.dto.LoginResponse;
import com.passport.auth.dto.MeResponse;
import com.passport.auth.dto.SignupRequest;
import com.passport.auth.dto.SignupResponse;
import com.passport.auth.repository.UserRepository;
import com.passport.global.error.BusinessException;
import com.passport.global.error.ErrorCode;
import com.passport.global.security.JwtTokenProvider;
import com.passport.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();

        User saved = userRepository.save(user);
        return new SignupResponse(saved.getId(), saved.getEmail(), saved.getNickname());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createToken(user.getId(), user.getEmail());
        return LoginResponse.of(accessToken, user.getId(), user.getNickname());
    }

    public MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Long profileId = profileRepository.findByUserId(userId)
                .map(profile -> profile.getId())
                .orElse(null);

        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), profileId);
    }
}
