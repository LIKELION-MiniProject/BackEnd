package com.passport.course.service;

import com.passport.course.domain.Course;
import com.passport.course.dto.CourseCreateRequest;
import com.passport.course.dto.CourseResponse;
import com.passport.course.dto.CourseUpdateRequest;
import com.passport.course.repository.CourseRepository;
import com.passport.global.error.BusinessException;
import com.passport.global.error.ErrorCode;
import com.passport.persona.service.PersonaService;
import com.passport.profile.domain.Profile;
import com.passport.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final ProfileService profileService;
    private final PersonaService personaService;

    @Transactional
    public CourseResponse create(Long profileId, Long userId, CourseCreateRequest request) {
        Profile profile = profileService.findOwnedProfile(profileId, userId);

        Course course = Course.builder()
                .profile(profile)
                .name(request.name())
                .credit(request.credit())
                .category(request.category())
                .grade(request.grade())
                .year(request.year())
                .semester(request.semester())
                .retake(request.retake())
                .build();

        CourseResponse response = CourseResponse.from(courseRepository.save(course));
        personaService.refresh(profile);   // 성적 변경 → 성향(persona) 재분석·저장(원석 요청, 최선노력)
        return response;
    }

    public List<CourseResponse> list(Long profileId, Long userId) {
        profileService.findOwnedProfile(profileId, userId);
        return courseRepository.findAllByProfileId(profileId).stream()
                .map(CourseResponse::from)
                .toList();
    }

    @Transactional
    public CourseResponse update(Long profileId, Long userId, Long courseId, CourseUpdateRequest request) {
        Course course = findOwnedCourse(profileId, userId, courseId);
        course.update(request.name(), request.credit(), request.category(), request.grade(), request.year(), request.semester(), request.retake());
        CourseResponse response = CourseResponse.from(course);
        personaService.refresh(course.getProfile());
        return response;
    }

    @Transactional
    public void delete(Long profileId, Long userId, Long courseId) {
        Course course = findOwnedCourse(profileId, userId, courseId);
        Profile profile = course.getProfile();
        courseRepository.delete(course);
        personaService.refresh(profile);
    }

    private Course findOwnedCourse(Long profileId, Long userId, Long courseId) {
        profileService.findOwnedProfile(profileId, userId);
        return courseRepository.findByIdAndProfileId(courseId, profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
    }
}
