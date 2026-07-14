package com.passport.requirement.controller;

import com.passport.global.common.ApiResponse;
import com.passport.requirement.dto.RequirementResponse;
import com.passport.requirement.service.RequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/requirements")
@RequiredArgsConstructor
public class RequirementController {

    private final RequirementService requirementService;

    @GetMapping("/{deptCode}")
    public ApiResponse<RequirementResponse> get(@PathVariable String deptCode) {
        return ApiResponse.success(RequirementResponse.from(requirementService.getByDeptCode(deptCode)));
    }
}
