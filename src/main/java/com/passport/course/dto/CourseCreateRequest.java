package com.passport.course.dto;

import com.passport.course.domain.Course;

public record CourseCreateRequest(
        String name,
        double credit,
        Course.CourseCategory category,
        Course.Grade grade,
        int year,
        int semester,
        boolean retake
) {
}
