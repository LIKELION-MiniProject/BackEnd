package com.passport.recommendation.dto;

import java.util.List;

/**
 * 계약 v2.1: AI 과목 추천 응답 (B안 — 5방향 전부 1회 생성).
 * recommendations(최상위)는 defaultDirectionId 방향의 미러(하위호환용).
 * source는 live | cache | fallback. "기본 추천" 배지는 fallback일 때만 노출(FE).
 */
public record RecommendationResponse(
        List<DirectionDto> directions,
        List<RecoItemDto> recommendations,
        String defaultDirectionId,
        String source,
        String generatedAt,
        /** additive(계약 v2.1): 성향 persona 블록. 규칙 폴백·구버전 캐시에는 없을 수 있어 null 허용 — FE는 null이면 미표시. */
        PersonaDto persona
) {
}
