package com.passport.dashboard.service;

import com.passport.certification.domain.Certification.CertificationStatus;
import com.passport.certification.domain.Certification.CertificationType;
import com.passport.course.domain.Course;
import com.passport.course.domain.Course.CourseCategory;
import com.passport.course.repository.CourseRepository;
import com.passport.course.service.GpaCalculator;
import com.passport.dashboard.dto.DashboardResponse;
import com.passport.dashboard.dto.DashboardResponse.CategoryView;
import com.passport.dashboard.dto.DashboardResponse.CourseLine;
import com.passport.diagnosis.dto.DiagnosisResponse;
import com.passport.diagnosis.dto.DiagnosisResponse.CategoryProgress;
import com.passport.diagnosis.dto.DiagnosisResponse.CertificationProgress;
import com.passport.diagnosis.service.DiagnosisService;
import com.passport.gpa.dto.GpaTrendResponse;
import com.passport.gpa.service.GpaTrendService;
import com.passport.persona.service.PersonaService;
import com.passport.profile.domain.Profile;
import com.passport.profile.service.ProfileService;
import com.passport.requirement.domain.EffectiveRequirement;
import com.passport.requirement.domain.UserGraduationRequirement.GraduationExamType;
import com.passport.requirement.service.RequirementResolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final Set<CourseCategory> MAJOR_CATEGORIES = EnumSet.of(
            CourseCategory.MAJOR_REQUIRED, CourseCategory.MAJOR_ELECTIVE, CourseCategory.MAJOR_BASIC);
    private static final Set<CourseCategory> LIBERAL_CATEGORIES = EnumSet.of(
            CourseCategory.GE_REQUIRED, CourseCategory.GE_ELECTIVE);
    /** "필수 과목" 근사치 계산에 쓰는 카테고리 — ⚠️ 특정 과목 카탈로그가 없어 전필+교필 이수 건수로 근사(아래 requiredCourseView 참고) */
    private static final Set<CourseCategory> REQUIRED_COURSE_CATEGORIES = EnumSet.of(
            CourseCategory.MAJOR_REQUIRED, CourseCategory.GE_REQUIRED);
    /** 핵심교양은 항상 5영역 고정(요건 입력 화면 §3, CoreLiberalArea @ElementCollection 5행) — 지어낸 값이 아니라 스키마 상수. */
    private static final int CORE_LIBERAL_AREA_TOTAL = 5;

    private final ProfileService profileService;
    private final CourseRepository courseRepository;
    private final DiagnosisService diagnosisService;
    private final GpaTrendService gpaTrendService;
    private final RequirementResolutionService requirementResolutionService;
    private final PersonaService personaService;

    public DashboardResponse getDashboard(Long profileId, Long userId) {
        Profile profile = profileService.findOwnedProfile(profileId, userId);
        EffectiveRequirement requirement = requirementResolutionService.resolve(profile);
        DiagnosisResponse diagnosis = diagnosisService.diagnose(profileId, userId);
        GpaTrendResponse gpaTrend = gpaTrendService.getTrend(profileId, userId);
        List<Course> courses = courseRepository.findAllByProfileId(profileId);

        var shortfalls = diagnosis.categories().stream()
                .filter(category -> category.shortfall() > 0)
                .toList();

        int overallProgress = overallProgress(diagnosis.totalCredit().earned(), requirement.totalCredit());
        List<CategoryView> categories = buildCategories(diagnosis, requirement, courses);
        SemesterSummary summary = latestSemesterSummary(courses);

        return new DashboardResponse(
                profileId,
                diagnosis.deptCode(),
                diagnosis.eligibleForGraduation(),
                diagnosis.totalCredit(),
                shortfalls,
                diagnosis.certifications(),
                diagnosis.gpa(),
                gpaTrend.semesters(),
                overallProgress,
                categories,
                summary.semester(),
                summary.requestedCredits(),
                summary.earnedCredits(),
                summary.gpa(),
                summary.courses(),
                personaService.get(profileId).orElse(null)
        );
    }

    private int overallProgress(int earned, int required) {
        if (required <= 0) {
            return 0;
        }
        return Math.min(100, Math.round(earned * 100f / required));
    }

    private List<CategoryView> buildCategories(DiagnosisResponse diagnosis, EffectiveRequirement requirement, List<Course> courses) {
        Map<CourseCategory, CategoryProgress> byCategory = diagnosis.categories().stream()
                .collect(Collectors.toMap(CategoryProgress::category, c -> c));

        int majorEarned = sumEarned(byCategory, MAJOR_CATEGORIES);
        int liberalEarned = sumEarned(byCategory, LIBERAL_CATEGORIES);

        int certRequired = (int) diagnosis.certifications().stream().filter(CertificationProgress::required).count();
        int certCurrent = (int) diagnosis.certifications().stream()
                .filter(c -> c.required() && c.fulfilled())
                .count();

        return List.of(
                categoryView("totalCredits", "총 이수학점", diagnosis.totalCredit().earned(), requirement.totalCredit(), "학점"),
                categoryView("major", "전공", majorEarned, requirement.majorTotalCredit(), "학점"),
                categoryView("liberal", "교양", liberalEarned, requirement.liberalTotalCredit(), "학점"),
                // ⚠️ 과목↔영역 매핑 데이터가 없어 "완료한 영역"을 직접 계산할 수는 없다.
                // 대신 유저가 요건 입력 화면(§3)에서 대상으로 표시(target=true)한 영역 수를 current로 보여준다
                // (완료 여부가 아니라 "적용 대상 영역 수" 근사치 — coreLiberalTargetCount는 유저 저장값 기반, 지어낸 값 아님).
                categoryView("coreLiberal", "핵심 교양", requirement.coreLiberalTargetCount(), CORE_LIBERAL_AREA_TOTAL, "영역"),
                requiredCourseView(courses),
                categoryView("certification", "졸업 인증", certCurrent, certRequired, null),
                examView(requirement.graduationExam(), diagnosis.certifications())
        );
    }

    private int sumEarned(Map<CourseCategory, CategoryProgress> byCategory, Set<CourseCategory> targets) {
        return targets.stream()
                .map(byCategory::get)
                .filter(Objects::nonNull)
                .mapToInt(CategoryProgress::earnedCredit)
                .sum();
    }

    /**
     * ⚠️ "필수 과목"은 학과가 지정한 특정 과목 카탈로그가 없어 정확 계산이 불가하다.
     * 전공필수+교양필수 카테고리의 이수 건수를 근사치(current)로 쓰고, 총 필수과목 수(required)는 알 수 없어 null로 둔다.
     */
    private CategoryView requiredCourseView(List<Course> courses) {
        int current = (int) courses.stream()
                .filter(c -> REQUIRED_COURSE_CATEGORIES.contains(c.getCategory()))
                .filter(c -> c.getGrade().isCreditEarned())
                .count();
        return new CategoryView("requiredCourse", "필수 과목", current, null, "건", "진행중");
    }

    /** ⚠️ graduationExam=EXAM(졸업시험)은 합격 여부를 추적하는 데이터가 없어 항상 "진행중"으로 둔다. */
    private CategoryView examView(GraduationExamType examType, List<CertificationProgress> certifications) {
        String status;
        if (examType == null || examType == GraduationExamType.NONE) {
            status = "완료";
        } else if (examType == GraduationExamType.THESIS || examType == GraduationExamType.EITHER) {
            boolean thesisPassed = certifications.stream()
                    .anyMatch(c -> c.type() == CertificationType.THESIS && c.status() == CertificationStatus.PASS);
            status = thesisPassed ? "완료" : "진행중";
        } else {
            status = "진행중";
        }
        return new CategoryView("exam", "졸업시험/논문", null, null, null, status);
    }

    private CategoryView categoryView(String key, String name, int current, int required, String unit) {
        String status = current >= required ? "완료" : "진행중";
        return new CategoryView(key, name, current, required, unit, status);
    }

    private SemesterSummary latestSemesterSummary(List<Course> courses) {
        if (courses.isEmpty()) {
            return new SemesterSummary(null, 0, 0, null, List.of());
        }

        Course latest = courses.stream()
                .max(Comparator.comparingInt(Course::getYear).thenComparingInt(Course::getSemester))
                .orElseThrow();

        List<Course> semesterCourses = courses.stream()
                .filter(c -> c.getYear() == latest.getYear() && c.getSemester() == latest.getSemester())
                .toList();

        int requested = semesterCourses.stream().mapToInt(Course::getCredit).sum();
        int earned = semesterCourses.stream()
                .filter(c -> c.getGrade().isCreditEarned())
                .mapToInt(Course::getCredit)
                .sum();
        Double gpa = GpaCalculator.calculate(semesterCourses);

        List<CourseLine> lines = semesterCourses.stream()
                .map(c -> new CourseLine(c.getCategory().getKoreanLabel(), c.getName(), c.getCredit(), c.getGrade().getLabel()))
                .toList();

        String semesterLabel = latest.getYear() + "-" + latest.getSemester() + "학기";
        return new SemesterSummary(semesterLabel, requested, earned, gpa, lines);
    }

    private record SemesterSummary(String semester, int requestedCredits, int earnedCredits, Double gpa, List<CourseLine> courses) {
    }
}
