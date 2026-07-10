package com.passport.course.service;

import com.passport.course.domain.Course;
import com.passport.course.dto.CourseCreateRequest;
import com.passport.course.dto.CourseResponse;
import com.passport.course.dto.CourseUpdateRequest;
import com.passport.course.repository.CourseRepository;
import com.passport.global.error.BusinessException;
import com.passport.global.error.ErrorCode;
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
                .build();

        return CourseResponse.from(courseRepository.save(course));
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
        course.update(request.name(), request.credit(), request.category(), request.grade(), request.year(), request.semester());
        return CourseResponse.from(course);
    }

    @Transactional
    public void delete(Long profileId, Long userId, Long courseId) {
        courseRepository.delete(findOwnedCourse(profileId, userId, courseId));
    }

    private Course findOwnedCourse(Long profileId, Long userId, Long courseId) {
        profileService.findOwnedProfile(profileId, userId);
        return courseRepository.findByIdAndProfileId(courseId, profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
    }
}
