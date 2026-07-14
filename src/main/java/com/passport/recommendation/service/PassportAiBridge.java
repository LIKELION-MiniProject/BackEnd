package com.passport.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passport.recommendation.dto.RecommendationResponse;
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
 * Spring → passport-ai(Python) 브릿지. 유저 데이터가 변경됐을 때 bridge.py를 subprocess로 실행해
 * 최신 데이터 기반의 AI 5방향 추천을 라이브로 생성한다.
 *
 * 계약: payload(JSON)를 stdin으로 주면 stdout으로 계약 v2.1 결과 JSON 하나가 나온다.
 * 실패(비정상 종료·타임아웃·파싱 실패) 시 Optional.empty() — 폴백 판단은 호출자(RecommendationService) 몫.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassportAiBridge {

    private static final int TIMEOUT_SECONDS = 60;

    private final ObjectMapper objectMapper;

    @Value("${passport-ai.python-command:python}")
    private String pythonCommand;

    @Value("${passport-ai.dir:passport-ai}")
    private String aiDir;

    public Optional<RecommendationResponse> generate(Map<String, Object> payload) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(pythonCommand, "bridge.py");
            builder.directory(new File(aiDir));
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            process = builder.start();

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(objectMapper.writeValueAsBytes(payload));
            }

            // stdout/stderr는 별도 스레드로 소비 — 파이프 버퍼가 차서 프로세스가 멈추는 것을 방지
            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("passport-ai 브릿지 타임아웃 ({}초 초과)", TIMEOUT_SECONDS);
                return Optional.empty();
            }

            String err = stderr.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                log.warn("passport-ai 브릿지 비정상 종료 (exit={}): {}", process.exitValue(), err);
                return Optional.empty();
            }

            log.info("passport-ai 브릿지 성공: {}", err.strip());
            return Optional.of(objectMapper.readValue(stdout.get(5, TimeUnit.SECONDS), RecommendationResponse.class));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("passport-ai 브릿지 인터럽트", e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("passport-ai 브릿지 실행 실패: {}", e.getMessage());
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
