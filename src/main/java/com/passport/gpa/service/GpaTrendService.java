package com.passport.gpa.service;

import com.passport.course.domain.Course;
import com.passport.course.domain.Course.CourseCategory;
import com.passport.course.repository.CourseRepository;
import com.passport.course.service.GpaCalculator;
import com.passport.gpa.dto.GpaTrendResponse;
import com.passport.gpa.dto.GpaTrendResponse.CategoryGpa;
import com.passport.gpa.dto.GpaTrendResponse.SemesterGpa;
import com.passport.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GpaTrendService {

    /** 진단결과 "과목 영역별 강점" 화면의 4그룹 매핑. 전공기초(MAJOR_BASIC)는 "기초"로 따로 뺀다. */
    private static final List<CategoryGroup> CATEGORY_GROUPS = List.of(
            new CategoryGroup("major", "전공", EnumSet.of(CourseCategory.MAJOR_REQUIRED, CourseCategory.MAJOR_ELECTIVE)),
            new CategoryGroup("basic", "기초", EnumSet.of(CourseCategory.MAJOR_BASIC)),
            new CategoryGroup("liberal", "교양", EnumSet.of(CourseCategory.GE_REQUIRED, CourseCategory.GE_ELECTIVE)),
            new CategoryGroup("etc", "기타", EnumSet.of(CourseCategory.GENERAL_ELECTIVE))
    );

    private final ProfileService profileService;
    private final CourseRepository courseRepository;

    public GpaTrendResponse getTrend(Long profileId, Long userId) {
        profileService.findOwnedProfile(profileId, userId);
        List<Course> courses = courseRepository.findAllByProfileId(profileId);

        Map<SemesterKey, List<Course>> bySemester = courses.stream()
                .collect(Collectors.groupingBy(course -> new SemesterKey(course.getYear(), course.getSemester())));

        List<SemesterGpa> semesters = bySemester.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toSemesterGpa(entry.getKey(), entry.getValue()))
                .toList();

        List<SemesterGpa> withPrediction = new ArrayList<>(semesters);
        SemesterGpa predicted = predictNext(semesters);
        if (predicted != null) {
            withPrediction.add(predicted);
        }

        return new GpaTrendResponse(withPrediction, GpaCalculator.calculate(courses), buildCategoryGpa(courses));
    }

    private SemesterGpa toSemesterGpa(SemesterKey key, List<Course> semesterCourses) {
        double earnedCredit = semesterCourses.stream()
                .filter(course -> course.getGrade().isCreditEarned())
                .mapToDouble(Course::getCredit)
                .sum();

        return new SemesterGpa(key.year(), key.semester(), GpaCalculator.calculate(semesterCourses), earnedCredit, false);
    }

    /**
     * 다음 학기 GPA를 직전 추세로 단순 외삽(규칙 계산, AI 아님).
     * 실제 GPA 학기가 2개 이상이면 최근 두 학기의 변화폭을 그대로 이어 붙이고, 1개뿐이면 그 값을 유지한다.
     * GPA 대상 학기가 하나도 없으면(성적 데이터 없음) 예측하지 않는다 — 근거 없는 값을 만들지 않는다.
     */
    private SemesterGpa predictNext(List<SemesterGpa> semesters) {
        List<SemesterGpa> withGpa = semesters.stream().filter(s -> s.gpa() != null).toList();
        if (withGpa.isEmpty()) {
            return null;
        }

        SemesterGpa last = withGpa.get(withGpa.size() - 1);
        double predictedGpa = last.gpa();
        if (withGpa.size() >= 2) {
            SemesterGpa prev = withGpa.get(withGpa.size() - 2);
            predictedGpa = last.gpa() + (last.gpa() - prev.gpa());
        }
        predictedGpa = Math.max(0.0, Math.min(4.5, predictedGpa));

        int nextYear = last.semester() == 1 ? last.year() : last.year() + 1;
        int nextSemester = last.semester() == 1 ? 2 : 1;
        return new SemesterGpa(nextYear, nextSemester, predictedGpa, 0, true);
    }

    private List<CategoryGpa> buildCategoryGpa(List<Course> courses) {
        return CATEGORY_GROUPS.stream()
                .map(group -> new CategoryGpa(group.key(), group.label(),
                        GpaCalculator.calculate(courses.stream().filter(c -> group.categories().contains(c.getCategory())).toList())))
                .toList();
    }

    private record CategoryGroup(String key, String label, Set<CourseCategory> categories) {
    }

    private record SemesterKey(int year, int semester) implements Comparable<SemesterKey> {
        @Override
        public int compareTo(SemesterKey other) {
            int yearCompare = Integer.compare(year, other.year);
            return yearCompare != 0 ? yearCompare : Integer.compare(semester, other.semester);
        }
    }
}
