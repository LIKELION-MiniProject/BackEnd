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

    /** 학점 상한. 한 과목이 이보다 클 수 없다(오타·잘못된 파싱 차단). */
    private static final double MAX_CREDIT = 30.0;

    /**
     * 학점은 0 이상 0.5 단위만 허용한다(예: 0, 0.5, 3).
     * credit이 int였을 때는 타입 자체가 가드였으나 0.5학점 과목(인성과미래설계Ⅰ) 때문에 double로 바뀌어
     * 3.333 같은 값이 그대로 저장될 수 있으므로 동등한 가드를 명시적으로 둔다.
     */
    private void validateCredit(double credit) {
        boolean halfStep = Math.rint(credit * 2) == credit * 2;
        if (credit < 0 || credit > MAX_CREDIT || !halfStep) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "학점은 0 이상 " + (int) MAX_CREDIT + " 이하의 0.5 단위여야 합니다.");
        }
    }

    @Transactional
    public CourseResponse create(Long profileId, Long userId, CourseCreateRequest request) {
        Profile profile = profileService.findOwnedProfile(profileId, userId);
        validateCredit(request.credit());

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
        validateCredit(request.credit());
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
