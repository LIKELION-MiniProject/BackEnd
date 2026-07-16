package com.passport.gpa.dto;

import java.util.List;

public record GpaTrendResponse(List<SemesterGpa> semesters, Double cumulativeGpa, List<CategoryGpa> categoryGpa) {

    /**
     * gpa가 null이면 그 학기에 GPA 대상 과목(P/NP 제외)이 없다는 뜻.
     * predicted=true면 실제 이수 학기가 아니라 직전 추세를 단순 외삽한 "다음 학기 예측"이다(규칙 계산, AI 아님).
     * 예측 학기는 earnedCredit=0(아직 이수하지 않음)으로 내려간다.
     */
    public record SemesterGpa(int year, int semester, Double gpa, double earnedCredit, boolean predicted) {
    }

    /** 이수구분을 전공/기초/교양/기타 4그룹으로 묶은 평균 GPA(진단결과 "과목 영역별 강점" 화면용). 그룹에 과목이 없으면 gpa=null. */
    public record CategoryGpa(String key, String label, Double gpa) {
    }
}
