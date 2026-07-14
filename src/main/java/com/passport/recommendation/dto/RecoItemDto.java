package com.passport.recommendation.dto;

import java.util.List;

/** 계약 v2.1: 추천 과목 1건. reasons는 정확히 3개, 순서 고정([성향맞춤, 과목특성, 졸업·전공기여]). */
public record RecoItemDto(
        String courseCode,
        String courseName,
        int credit,
        String category,
        List<String> reasons
) {
}
