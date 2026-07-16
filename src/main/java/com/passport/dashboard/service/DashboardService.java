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
    private static final String STATUS_DONE = "완료";
    private static final String STATUS_IN_PROGRESS = "진행중";
    /** 판정 근거 데이터가 없어 충족/미충족을 계산할 수 없는 항목. FE는 막대·퍼센트를 그리지 않는다. */
    private static final String STATUS_UNKNOWN = "미입력";

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

    private int overallProgress(double earned, int required) {
        if (required <= 0) {
            return 0;
        }
        return Math.min(100, Math.round((float) (earned * 100.0 / required)));
    }

    private List<CategoryView> buildCategories(DiagnosisResponse diagnosis, EffectiveRequirement requirement, List<Course> courses) {
        Map<CourseCategory, CategoryProgress> byCategory = diagnosis.categories().stream()
                .collect(Collectors.toMap(CategoryProgress::category, c -> c));

        double majorEarned = sumEarned(byCategory, MAJOR_CATEGORIES);
        double liberalEarned = sumEarned(byCategory, LIBERAL_CATEGORIES);

        int certRequired = (int) diagnosis.certifications().stream().filter(CertificationProgress::required).count();
        int certCurrent = (int) diagnosis.certifications().stream()
                .filter(c -> c.required() && c.fulfilled())
                .count();

        return List.of(
                categoryView("totalCredits", "총 이수학점", diagnosis.totalCredit().earned(), requirement.totalCredit(), "학점"),
                categoryView("major", "전공", majorEarned, requirement.majorTotalCredit(), "학점"),
                categoryView("liberal", "교양", liberalEarned, requirement.liberalTotalCredit(), "학점"),
                coreLiberalView(),
                requiredCourseView(courses),
                categoryView("certification", "졸업 인증", certCurrent, certRequired, null),
                examView(requirement.graduationExam(), diagnosis.certifications())
        );
    }

    private double sumEarned(Map<CourseCategory, CategoryProgress> byCategory, Set<CourseCategory> targets) {
        return targets.stream()
                .map(byCategory::get)
                .filter(Objects::nonNull)
                .mapToDouble(CategoryProgress::earnedCredit)
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
        return new CategoryView("requiredCourse", "필수 과목", (double) current, null, "건", STATUS_IN_PROGRESS);
    }

    /**
     * 졸업시험/논문은 THESIS·EITHER일 때만 인증 데이터로 판정할 수 있다.
     * EXAM(졸업시험 합격 여부)은 추적 데이터가 없고, NONE·null은 요건 자체가 미확정이라 판정 근거가 없다
     * — 근거 없이 "완료"로 내리면 미충족을 충족으로 오표시하므로 STATUS_UNKNOWN으로 둔다.
     */
    private CategoryView examView(GraduationExamType examType, List<CertificationProgress> certifications) {
        if (examType != GraduationExamType.THESIS && examType != GraduationExamType.EITHER) {
            return new CategoryView("exam", "졸업시험/논문", null, null, null, STATUS_UNKNOWN);
        }
        boolean thesisPassed = certifications.stream()
                .anyMatch(c -> c.type() == CertificationType.THESIS && c.status() == CertificationStatus.PASS);
        return new CategoryView("exam", "졸업시험/논문", null, null, null, thesisPassed ? STATUS_DONE : STATUS_IN_PROGRESS);
    }

    /**
     * 핵심교양은 과목↔영역 매핑 데이터가 없어 "이수한 영역 수"를 계산할 수 없다.
     * 유저 저장값의 target 수는 "적용 대상 영역"이지 이수 실적이 아니므로 진척도로 내보내면
     * 5영역 전부 대상 표시 시 5/5 "완료"로 오표시된다. 판정 불가를 그대로 알린다.
     */
    private CategoryView coreLiberalView() {
        return new CategoryView("coreLiberal", "핵심 교양", null, null, null, STATUS_UNKNOWN);
    }

    private CategoryView categoryView(String key, String name, double current, int required, String unit) {
        String status = current >= required ? STATUS_DONE : STATUS_IN_PROGRESS;
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

        double requested = semesterCourses.stream().mapToDouble(Course::getCredit).sum();
        double earned = semesterCourses.stream()
                .filter(c -> c.getGrade().isCreditEarned())
                .mapToDouble(Course::getCredit)
                .sum();
        Double gpa = GpaCalculator.calculate(semesterCourses);

        List<CourseLine> lines = semesterCourses.stream()
                .map(c -> new CourseLine(c.getCategory().getKoreanLabel(), c.getName(), c.getCredit(), c.getGrade().getLabel()))
                .toList();

        String semesterLabel = latest.getYear() + "-" + latest.getSemester() + "학기";
        return new SemesterSummary(semesterLabel, requested, earned, gpa, lines);
    }

    private record SemesterSummary(String semester, double requestedCredits, double earnedCredits, Double gpa, List<CourseLine> courses) {
    }
}
