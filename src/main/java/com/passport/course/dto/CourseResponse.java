package com.passport.course.dto;

import com.passport.course.domain.Course;

public record CourseResponse(
        Long id,
        String name,
        double credit,
        Course.CourseCategory category,
        Course.Grade grade,
        int year,
        int semester,
        boolean retake
) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getName(),
                course.getCredit(),
                course.getCategory(),
                course.getGrade(),
                course.getYear(),
                course.getSemester(),
                course.isRetake()
        );
    }
}
