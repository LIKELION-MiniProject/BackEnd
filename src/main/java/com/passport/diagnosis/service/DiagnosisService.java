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
import com.passport.diagnosis.dto.DiagnosisResponse.GpaProgress;
import com.passport.profile.domain.Profile;
import com.passport.profile.service.ProfileService;
import com.passport.requirement.domain.GraduationRequirement;
import com.passport.requirement.service.RequirementService;
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
    private final RequirementService requirementService;

    public DiagnosisResponse diagnose(Long profileId, Long userId) {
        Profile profile = profileService.findOwnedProfile(profileId, userId);
        GraduationRequirement requirement = requirementService.getByDeptCode(profile.getDeptCode());
        List<Course> courses = courseRepository.findAllByProfileId(profileId);

        CreditProgress totalCredit = calculateTotalCredit(courses, requirement);
        List<CategoryProgress> categories = calculateCategoryProgress(courses, requirement);
        List<CertificationProgress> certifications = calculateCertificationProgress(profileId, requirement);
        GpaProgress gpa = calculateGpaProgress(courses, requirement);

        boolean creditsOk = totalCredit.shortfall() == 0
                && categories.stream().allMatch(c -> c.shortfall() == 0);
        boolean certsOk = certifications.stream().allMatch(CertificationProgress::fulfilled);
        boolean eligible = creditsOk && certsOk && gpa.fulfilled();

        return new DiagnosisResponse(requirement.deptCode(), totalCredit, categories, certifications, gpa, eligible);
    }

    private CreditProgress calculateTotalCredit(List<Course> courses, GraduationRequirement requirement) {
        int earned = courses.stream()
                .filter(course -> course.getGrade().isCreditEarned())
                .mapToInt(Course::getCredit)
                .sum();
        return CreditProgress.of(earned, requirement.totalCredit());
    }

    private List<CategoryProgress> calculateCategoryProgress(List<Course> courses, GraduationRequirement requirement) {
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
                CategoryProgress.of(CourseCategory.MAJOR_REQUIRED, earnedByCategory.get(CourseCategory.MAJOR_REQUIRED), requirement.majorRequiredCredit()),
                CategoryProgress.of(CourseCategory.MAJOR_ELECTIVE, earnedByCategory.get(CourseCategory.MAJOR_ELECTIVE), requirement.majorElectiveCredit()),
                CategoryProgress.of(CourseCategory.GE_REQUIRED, earnedByCategory.get(CourseCategory.GE_REQUIRED), requirement.geRequiredCredit()),
                CategoryProgress.of(CourseCategory.GE_ELECTIVE, earnedByCategory.get(CourseCategory.GE_ELECTIVE), requirement.geElectiveCredit()),
                CategoryProgress.of(CourseCategory.GENERAL_ELECTIVE, earnedByCategory.get(CourseCategory.GENERAL_ELECTIVE), requirement.generalElectiveCredit())
        );
    }

    private List<CertificationProgress> calculateCertificationProgress(Long profileId, GraduationRequirement requirement) {
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

    private GpaProgress calculateGpaProgress(List<Course> courses, GraduationRequirement requirement) {
        Double currentGpa = GpaCalculator.calculate(courses);
        boolean fulfilled = currentGpa != null && currentGpa >= requirement.minGpa();
        return new GpaProgress(currentGpa, requirement.minGpa(), fulfilled);
    }
}
