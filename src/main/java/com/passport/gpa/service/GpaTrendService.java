package com.passport.gpa.service;

import com.passport.course.domain.Course;
import com.passport.course.repository.CourseRepository;
import com.passport.course.service.GpaCalculator;
import com.passport.gpa.dto.GpaTrendResponse;
import com.passport.gpa.dto.GpaTrendResponse.SemesterGpa;
import com.passport.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GpaTrendService {

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

        return new GpaTrendResponse(semesters, GpaCalculator.calculate(courses));
    }

    private SemesterGpa toSemesterGpa(SemesterKey key, List<Course> semesterCourses) {
        int earnedCredit = semesterCourses.stream()
                .filter(course -> course.getGrade().isCreditEarned())
                .mapToInt(Course::getCredit)
                .sum();

        return new SemesterGpa(key.year(), key.semester(), GpaCalculator.calculate(semesterCourses), earnedCredit);
    }

    private record SemesterKey(int year, int semester) implements Comparable<SemesterKey> {
        @Override
        public int compareTo(SemesterKey other) {
            int yearCompare = Integer.compare(year, other.year);
            return yearCompare != 0 ? yearCompare : Integer.compare(semester, other.semester);
        }
    }
}
