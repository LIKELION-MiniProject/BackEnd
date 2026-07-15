package com.passport.recommendation.dto;

import java.util.List;

/**
 * 계약 v2.1 additive: 학습 성향 persona 블록. 홈(대시보드)·AI분석 홈·진단결과 3화면이 공유한다.
 * passport-ai가 과거 성적×과목 특성으로 규칙 템플릿(AI 생성 아님)으로 만들고, 수강 이력이 바뀔 때마다
 * {@link com.passport.persona.service.PersonaService}가 재계산해 {@link com.passport.persona.domain.ProfilePersona}에
 * 저장한다. 홈·AI분석 응답은 그 저장값을 그대로 서빙(persona.dto 패키지가 도메인을 넘어 공유되는 이유).
 * 아직 수강 이력이 하나도 없는 신규 계정이거나 브릿지 실패 시 null일 수 있다 — FE는 null이면 블록 미표시.
 */
public record PersonaDto(
        String type,
        String label,
        String description,
        List<String> strategies,
        List<String> summary
) {
}
