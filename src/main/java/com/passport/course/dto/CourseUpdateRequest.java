package com.passport.course.dto;

import com.passport.course.domain.Course;

public record CourseUpdateRequest(
        String name,
        int credit,
        Course.CourseCategory category,
        Course.Grade grade,
        int year,
        int semester
) {
}
