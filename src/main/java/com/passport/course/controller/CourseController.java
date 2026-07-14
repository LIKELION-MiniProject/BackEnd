package com.passport.course.controller;

import com.passport.course.dto.CourseCreateRequest;
import com.passport.course.dto.CourseResponse;
import com.passport.course.dto.CourseUpdateRequest;
import com.passport.course.service.CourseService;
import com.passport.global.common.ApiResponse;
import com.passport.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long profileId, @RequestBody CourseCreateRequest request) {
        return ApiResponse.success(courseService.create(profileId, authUser.id(), request));
    }

    @GetMapping
    public ApiResponse<List<CourseResponse>> list(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long profileId) {
        return ApiResponse.success(courseService.list(profileId, authUser.id()));
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseResponse> update(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long profileId, @PathVariable Long courseId,
                                               @RequestBody CourseUpdateRequest request) {
        return ApiResponse.success(courseService.update(profileId, authUser.id(), courseId, request));
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                     @PathVariable Long profileId, @PathVariable Long courseId) {
        courseService.delete(profileId, authUser.id(), courseId);
        return ApiResponse.success(null);
    }
}
