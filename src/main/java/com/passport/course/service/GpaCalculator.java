package com.passport.course.service;

import com.passport.course.domain.Course;
import com.passport.course.domain.Course.Grade;

import java.util.List;

/**
 * GPA = Σ(학점 × 성적환산) / Σ(GPA 대상 학점). P/NP는 분자·분모 모두에서 제외.
 * (CLAUDE.md 부록 C) Diagnosis·GPA Trend 두 도메인이 동일한 계산 규칙을 공유하기 위해 여기 둠.
 */
public final class GpaCalculator {

    private GpaCalculator() {
    }

    /** GPA 대상 과목이 하나도 없으면 null (0으로 나누는 것을 방지) */
    public static Double calculate(List<Course> courses) {
        int gpaCredit = 0;
        double weightedSum = 0.0;

        for (Course course : courses) {
            Grade grade = course.getGrade();
            if (!grade.isGpaEligible()) {
                continue;
            }
            gpaCredit += course.getCredit();
            weightedSum += course.getCredit() * grade.getGpaValue();
        }

        return gpaCredit == 0 ? null : weightedSum / gpaCredit;
    }
}
