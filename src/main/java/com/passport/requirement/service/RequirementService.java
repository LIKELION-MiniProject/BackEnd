package com.passport.requirement.service;

import com.passport.global.error.BusinessException;
import com.passport.global.error.ErrorCode;
import com.passport.requirement.BigdataAiRequirement;
import com.passport.requirement.domain.GraduationRequirement;
import org.springframework.stereotype.Service;

@Service
public class RequirementService {

    public GraduationRequirement getByDeptCode(String deptCode) {
        if (!BigdataAiRequirement.DEPT_CODE.equals(deptCode)) {
            throw new BusinessException(ErrorCode.REQUIREMENT_NOT_FOUND);
        }
        return BigdataAiRequirement.REQUIREMENT;
    }
}
