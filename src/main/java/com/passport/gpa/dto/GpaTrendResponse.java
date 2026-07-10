package com.passport.gpa.dto;

import java.util.List;

public record GpaTrendResponse(List<SemesterGpa> semesters, Double cumulativeGpa) {

    /** gpa가 null이면 그 학기에 GPA 대상 과목(P/NP 제외)이 없다는 뜻 */
    public record SemesterGpa(int year, int semester, Double gpa, int earnedCredit) {
    }
}
