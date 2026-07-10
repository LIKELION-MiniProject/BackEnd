package com.passport.requirement;

import com.passport.requirement.domain.GraduationRequirement;

/**
 * 빅데이터 인공지능 전공 졸업 요건 (하드코딩, 한 학과만 지원).
 *
 * TODO: 아래 수치는 팀이 실제 학과 졸업요건 데이터를 확보하기 전까지 쓰는 placeholder입니다.
 * Diagnosis/GPA 로직의 구조를 먼저 완성하기 위한 값이며, 실제 수치가 확정되면 이 클래스만 교체하면 됩니다.
 */
public final class BigdataAiRequirement {

    /** Profile.deptCode와 매칭되는 학과 코드 — 팀 확정값으로 교체 필요 */
    public static final String DEPT_CODE = "BIGDATA_AI";

    public static final GraduationRequirement REQUIREMENT = new GraduationRequirement(
            DEPT_CODE,
            130,   // 총 이수학점
            21,    // 전공필수 최소학점
            39,    // 전공선택 최소학점
            14,    // 교양필수 최소학점
            21,    // 교양선택 최소학점
            35,    // 자유선택 최소학점
            true,  // 어학 인증 필수 여부
            true,  // 봉사 인증 필수 여부
            false, // 논문(졸업논문/캡스톤) 필수 여부
            2.0    // 졸업 최소 GPA
    );

    private BigdataAiRequirement() {
    }
}
