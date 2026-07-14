package com.passport.recommendation.dto;

import java.util.List;

/**
 * 계약 v2.1: 방향성 1개.
 * caution은 EXAM_SOLO·TEAM_ACTIVE 비-thin 상태에서만 값을 가지며, 그 외는 null.
 * thin=true면 recommendations 개수를 5개로 채우지 않고 매칭된 개수 그대로 내려준다.
 */
public record DirectionDto(
        String directionId,
        String name,
        String description,
        String caution,
        boolean thin,
        List<RecoItemDto> recommendations
) {
}
