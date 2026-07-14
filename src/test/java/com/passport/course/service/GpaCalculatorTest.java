package com.passport.course.service;

import com.passport.course.domain.Course;
import com.passport.course.domain.Course.CourseCategory;
import com.passport.course.domain.Course.Grade;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GpaCalculatorTest {

    @Test
    void P_NP과목은_GPA_계산에서_분자_분모_모두_제외한다() {
        List<Course> courses = List.of(
                course(3, Grade.A, 2026, 1),      // 3 * 4.0 = 12.0
                course(2, Grade.B_PLUS, 2026, 1),  // 2 * 3.5 = 7.0
                course(3, Grade.P, 2026, 1),        // 제외
                course(1, Grade.NP, 2026, 1)         // 제외
        );

        Double gpa = GpaCalculator.calculate(courses);

        // (12.0 + 7.0) / (3 + 2) = 3.8
        assertThat(gpa).isEqualTo(3.8);
    }

    @Test
    void F는_학점은_미인정이지만_GPA에는_반영된다() {
        List<Course> courses = List.of(
                course(3, Grade.A, 2026, 1),  // 3 * 4.0 = 12.0
                course(3, Grade.F, 2026, 1)    // 3 * 0.0 = 0.0, 분모엔 포함
        );

        Double gpa = GpaCalculator.calculate(courses);

        // (12.0 + 0.0) / (3 + 3) = 2.0
        assertThat(gpa).isEqualTo(2.0);

        long earnedCredit = courses.stream().filter(c -> c.getGrade().isCreditEarned()).mapToInt(Course::getCredit).sum();
        assertThat(earnedCredit).isEqualTo(3); // F 과목 3학점은 이수 인정 안 됨
    }

    @Test
    void GPA_대상_과목이_없으면_null을_반환한다() {
        List<Course> courses = List.of(course(3, Grade.P, 2026, 1));

        assertThat(GpaCalculator.calculate(courses)).isNull();
    }

    private Course course(int credit, Grade grade, int year, int semester) {
        return Course.builder()
                .profile(null)
                .name("테스트과목")
                .credit(credit)
                .category(CourseCategory.MAJOR_ELECTIVE)
                .grade(grade)
                .year(year)
                .semester(semester)
                .build();
    }
}
