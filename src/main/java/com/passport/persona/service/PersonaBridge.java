package com.passport.persona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passport.recommendation.dto.PersonaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Spring → passport-ai(Python) persona 전용 브릿지. bridge.py(AI 5방향 추천)와 달리
 * Gemini 호출이 없는 순수 규칙 계산(core/profile.py analyze+persona)만 실행해 훨씬 빠르다.
 * 수강 이력이 바뀔 때마다(CourseService) 호출해도 부담이 없도록 타임아웃도 짧게 둔다.
 *
 * 계약: payload(JSON)를 stdin으로 주면 stdout으로 persona dict(JSON) 하나가 나온다.
 * 실패(비정상 종료·타임아웃·파싱 실패) 시 Optional.empty() — 호출자는 기존 저장값을 그대로 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonaBridge {

    private static final int TIMEOUT_SECONDS = 20;

    private final ObjectMapper objectMapper;

    @Value("${passport-ai.python-command:python}")
    private String pythonCommand;

    @Value("${passport-ai.dir:passport-ai}")
    private String aiDir;

    public Optional<PersonaDto> analyze(Map<String, Object> payload) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(pythonCommand, "persona.py");
            builder.directory(new File(aiDir));
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            process = builder.start();

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(objectMapper.writeValueAsBytes(payload));
            }

            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("persona 브릿지 타임아웃 ({}초 초과)", TIMEOUT_SECONDS);
                return Optional.empty();
            }

            String err = stderr.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                log.warn("persona 브릿지 비정상 종료 (exit={}): {}", process.exitValue(), err);
                return Optional.empty();
            }

            log.info("persona 브릿지 성공: {}", err.strip());
            return Optional.of(objectMapper.readValue(stdout.get(5, TimeUnit.SECONDS), PersonaDto.class));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("persona 브릿지 인터럽트", e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("persona 브릿지 실행 실패: {}", e.getMessage());
            return Optional.empty();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        });
    }
}
