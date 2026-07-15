package com.passport.persona.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passport.course.repository.CourseRepository;
import com.passport.persona.domain.ProfilePersona;
import com.passport.persona.repository.ProfilePersonaRepository;
import com.passport.profile.domain.Profile;
import com.passport.profile.repository.ProfileRepository;
import com.passport.recommendation.dto.PersonaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 학습 성향(persona) 저장·조회. 수강 이력(성적)이 바뀔 때마다 CourseService가 refresh()를 호출해
 * regenerate 하고, 홈 대시보드·AI 추천 화면은 이 저장값을 읽기만 한다(원석 요청 — 조회할 때마다 재계산 X).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaService {

    private final ProfilePersonaRepository profilePersonaRepository;
    private final CourseRepository courseRepository;
    private final ProfileRepository profileRepository;
    private final PersonaBridge personaBridge;
    private final ObjectMapper objectMapper;

    @Value("${passport-ai.cache-dir}")
    private String cacheDir;

    /**
     * 규칙 기반 성향 재분석(Gemini 미호출) 후 DB 저장. 최선노력(best-effort) — 실패해도 예외를 던지지 않는다.
     * 이미 완료된 수강 이력 저장(트랜잭션)을 성향 분석 실패 때문에 되돌리지 않기 위함이다.
     */
    @Transactional
    public void refresh(Profile profile) {
        try {
            List<Map<String, Object>> history = courseRepository.findAllByProfileId(profile.getId()).stream()
                    .<Map<String, Object>>map(c -> Map.of("courseName", c.getName(), "grade", c.getGrade().getLabel()))
                    .toList();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("studentKey", profile.getStudentId());
            payload.put("history", history);

            Optional<PersonaDto> analyzed = personaBridge.analyze(payload);
            if (analyzed.isEmpty()) {
                log.warn("성향(persona) 재분석 실패 — 기존 저장값 유지 (profileId={})", profile.getId());
                return;
            }

            PersonaDto dto = analyzed.get();
            ProfilePersona entity = profilePersonaRepository.findByProfileId(profile.getId()).orElse(null);
            if (entity == null) {
                profilePersonaRepository.save(ProfilePersona.builder()
                        .profile(profile)
                        .type(dto.type())
                        .label(dto.label())
                        .description(dto.description())
                        .strategies(dto.strategies())
                        .summary(dto.summary())
                        .build());
            } else {
                entity.update(dto.type(), dto.label(), dto.description(), dto.strategies(), dto.summary());
            }
        } catch (Exception e) {
            log.warn("성향(persona) 갱신 중 예외 (profileId={}): {}", profile.getId(), e.getMessage());
        }
    }

    /**
     * 저장된 persona를 조회한다. 이 프로필이 아직 CourseService를 거친 적이 없으면(예: data.sql로 시드된
     * 데모 계정처럼 수강 이력이 CRUD가 아니라 직접 삽입된 경우) DB에 저장값이 없을 수 있다 — 이때는
     * passport-ai 추천 캐시(cache/{studentId}.json)에 남아있는 persona 스냅샷으로 폴백한다.
     * 둘 다 없으면 empty(FE 미표시).
     */
    @Transactional(readOnly = true)
    public Optional<PersonaDto> get(Long profileId) {
        Optional<PersonaDto> stored = profilePersonaRepository.findByProfileId(profileId).map(ProfilePersona::toDto);
        if (stored.isPresent()) {
            return stored;
        }
        return profileRepository.findById(profileId)
                .flatMap(profile -> readPersonaFromRecommendationCache(profile.getStudentId()));
    }

    private Optional<PersonaDto> readPersonaFromRecommendationCache(String studentId) {
        String safe = studentId.replaceAll("[^a-zA-Z0-9_-]", "");
        Path path = Path.of(cacheDir, safe + ".json");
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            JsonNode personaNode = root.get("persona");
            if (personaNode == null || personaNode.isNull()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.treeToValue(personaNode, PersonaDto.class));
        } catch (IOException e) {
            log.warn("추천 캐시에서 persona 읽기 실패 (studentId={}, path={}): {}", studentId, path, e.getMessage());
            return Optional.empty();
        }
    }
}
