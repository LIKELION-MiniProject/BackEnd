package com.passport.course.repository;

import com.passport.course.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findAllByProfileId(Long profileId);

    Optional<Course> findByIdAndProfileId(Long id, Long profileId);
}
