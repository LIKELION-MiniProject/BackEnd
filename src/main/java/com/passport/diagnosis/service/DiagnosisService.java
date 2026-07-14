package com.passport.diagnosis.service;

import com.passport.certification.domain.Certification;
import com.passport.certification.domain.Certification.CertificationStatus;
import com.passport.certification.domain.Certification.CertificationType;
import com.passport.certification.repository.CertificationRepository;
import com.passport.course.domain.Course;
import com.passport.course.domain.Course.CourseCategory;
import com.passport.course.repository.CourseRepository;
import com.passport.course.service.GpaCalculator;
import com.passport.diagnosis.dto.DiagnosisResponse;
import com.passport.diagnosis.dto.DiagnosisResponse.CategoryProgress;
import com.passport.diagnosis.dto.DiagnosisResponse.CertificationProgress;
import com.passport.diagnosis.dto.DiagnosisResponse.CreditProgress;
import com.passport.diagnosis.dto.DiagnosisResponse.AreaMark;
import com.passport.diagnosis.dto.DiagnosisResponse.GpaProgress;
import com.passport.diagnosis.dto.DiagnosisResponse.GraduationCertificationProgress;
import com.passport.profile.domain.Profile;
import com.passport.profile.service.ProfileService;
import com.passport.requirement.domain.CertMark;
import com.passport.requirement.domain.EffectiveRequirement;
import com.passport.requirement.domain.RequirementCertificationTargets;
import com.passport.requirement.service.RequirementResolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosisService {

    private final ProfileService profileService;
    private final CourseRepository courseRepository;
    private final CertificationRepository certificationRepository;
    private final RequirementResolutionService requirementResolutionService;

    public DiagnosisResponse diagnose(Long profileId, Long userId) {
        Profile profile = profileService.findOwnedProfile(profileId, userId);
        EffectiveRequirement requirement = requirementResolutionService.resolve(profile);
        List<Course> courses = courseRepository.findAllByProfileId(profileId);

        CreditProgress totalCredit = calculateTotalCredit(courses, requirement);
        List<CategoryProgress> categories = calculateCategoryProgress(courses, requirement);
        List<CertificationProgress> certifications = calculateCertificationProgress(profileId, requirement);
        GpaProgress gpa = calculateGpaProgress(courses, requirement);
        GraduationCertificationProgress graduationCertification =
                buildGraduationCertification(requirement.certificationTargets());

        boolean creditsOk = totalCredit.shortfall() == 0
                && categories.stream().allMatch(c -> c.shortfall() == 0);
        boolean certsOk = certifications.stream().allMatch(CertificationProgress::fulfilled);
        // 유저가 5분야를 입력했으면 그 판정도 AND(더 엄격해질 뿐, 기존보다 느슨해지지 않음). 미입력이면 null → 영향 없음.
        boolean gradCertOk = graduationCertification == null || graduationCertification.fulfilled();
        boolean eligible = creditsOk && certsOk && gpa.fulfilled() && gradCertOk;

        return new DiagnosisResponse(requirement.deptCode(), totalCredit, categories, certifications, gpa,
                eligible, graduationCertification);
    }

    /**
     * 졸업 인증 5분야 판정(유저 입력 기반).
     * 규칙: '대상(TARGET)'으로 표시한 분야가 전부 '완료(DONE)'여야 fulfilled=true.
     *       '비대상(NOT_TARGET)'·미표기(null)는 무시. 대학의 필수/택1 규칙을 창작하지 않는다.
     */
    private GraduationCertificationProgress buildGraduationCertification(RequirementCertificationTargets targets) {
        if (targets == null) {
            return null;   // 유저 요건 미저장 → 기존 동작 유지
        }
        List<AreaMark> areas = List.of(
                new AreaMark("foreignLangCert", targets.getForeignLangCert()),
                new AreaMark("infoProcessing", targets.getInfoProcessing()),
                new AreaMark("cpr", targets.getCpr()),
                new AreaMark("socialService", targets.getSocialService()),
                new AreaMark("foreignLangExtra", targets.getForeignLangExtra())
        );
        boolean fulfilled = areas.stream().noneMatch(a -> a.mark() == CertMark.TARGET);
        return new GraduationCertificationProgress(areas, fulfilled);
    }

    private CreditProgress calculateTotalCredit(List<Course> courses, EffectiveRequirement requirement) {
        int earned = courses.stream()
                .filter(course -> course.getGrade().isCreditEarned())
                .mapToInt(Course::getCredit)
                .sum();
        return CreditProgress.of(earned, requirement.totalCredit());
    }

    private List<CategoryProgress> calculateCategoryProgress(List<Course> courses, EffectiveRequirement requirement) {
        Map<CourseCategory, Integer> earnedByCategory = new EnumMap<>(CourseCategory.class);
        for (CourseCategory category : CourseCategory.values()) {
            earnedByCategory.put(category, 0);
        }
        for (Course course : courses) {
            if (course.getGrade().isCreditEarned()) {
                earnedByCategory.merge(course.getCategory(), course.getCredit(), Integer::sum);
            }
        }

        return List.of(
                CategoryProgress.of(CourseCategory.MAJOR_BASIC, earnedByCategory.get(CourseCategory.MAJOR_BASIC), requirement.majorBasicCredit()),
                CategoryProgress.of(CourseCategory.MAJOR_REQUIRED, earnedByCategory.get(CourseCategory.MAJOR_REQUIRED), requirement.majorRequiredCredit()),
                CategoryProgress.of(CourseCategory.MAJOR_ELECTIVE, earnedByCategory.get(CourseCategory.MAJOR_ELECTIVE), requirement.majorElectiveCredit()),
                CategoryProgress.of(CourseCategory.GE_REQUIRED, earnedByCategory.get(CourseCategory.GE_REQUIRED), requirement.geRequiredCredit()),
                CategoryProgress.of(CourseCategory.GE_ELECTIVE, earnedByCategory.get(CourseCategory.GE_ELECTIVE), requirement.geElectiveCredit()),
                CategoryProgress.of(CourseCategory.GENERAL_ELECTIVE, earnedByCategory.get(CourseCategory.GENERAL_ELECTIVE), requirement.generalElectiveCredit())
        );
    }

    private List<CertificationProgress> calculateCertificationProgress(Long profileId, EffectiveRequirement requirement) {
        Map<CertificationType, CertificationStatus> statusByType = certificationRepository.findAllByProfileId(profileId).stream()
                .collect(Collectors.toMap(Certification::getType, Certification::getStatus));

        return List.of(
                certificationProgress(CertificationType.LANGUAGE, requirement.languageCertRequired(), statusByType),
                certificationProgress(CertificationType.VOLUNTEER, requirement.volunteerCertRequired(), statusByType),
                certificationProgress(CertificationType.THESIS, requirement.thesisCertRequired(), statusByType)
        );
    }

    private CertificationProgress certificationProgress(CertificationType type, boolean required,
                                                          Map<CertificationType, CertificationStatus> statusByType) {
        CertificationStatus status = statusByType.getOrDefault(type, CertificationStatus.NOT_SUBMITTED);
        boolean fulfilled = !required || status == CertificationStatus.PASS;
        return new CertificationProgress(type, required, status, fulfilled);
    }

    private GpaProgress calculateGpaProgress(List<Course> courses, EffectiveRequirement requirement) {
        Double currentGpa = GpaCalculator.calculate(courses);
        boolean fulfilled = currentGpa != null && currentGpa >= requirement.minGpa();
        return new GpaProgress(currentGpa, requirement.minGpa(), fulfilled);
    }
}
