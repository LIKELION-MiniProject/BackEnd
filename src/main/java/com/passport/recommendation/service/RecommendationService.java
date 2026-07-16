package com.passport.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passport.certification.repository.CertificationRepository;
import com.passport.course.domain.Course;
import com.passport.course.domain.Course.CourseCategory;
import com.passport.course.repository.CourseRepository;
import com.passport.diagnosis.dto.DiagnosisResponse;
import com.passport.diagnosis.dto.DiagnosisResponse.CategoryProgress;
import com.passport.diagnosis.service.DiagnosisService;
import com.passport.persona.service.PersonaService;
import com.passport.profile.domain.Profile;
import com.passport.profile.service.ProfileService;
import com.passport.recommendation.dto.DirectionDto;
import com.passport.recommendation.dto.PersonaDto;
import com.passport.recommendation.dto.RecoItemDto;
import com.passport.recommendation.dto.RecommendationResponse;
import com.passport.requirement.domain.CoreLiberalArea;
import com.passport.requirement.domain.RequirementCertificationTargets;
import com.passport.requirement.domain.RequirementCredits;
import com.passport.requirement.repository.UserRequirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final ProfileService profileService;
    private final DiagnosisService diagnosisService;
    private final ObjectMapper objectMapper;
    private final PassportAiBridge passportAiBridge;
    private final CourseRepository courseRepository;
    private final CertificationRepository certificationRepository;
    private final UserRequirementRepository userRequirementRepository;
    private final PersonaService personaService;

    @Value("${passport-ai.cache-dir}")
    private String cacheDir;

    /** GET /recommendations — 조회 전용. AI를 부르지 않고 마지막 결과(캐시)를 서빙한다. */
    public RecommendationResponse getRecommendations(Long profileId, Long userId) {
        Profile profile = profileService.findOwnedProfile(profileId, userId);
        RecommendationResponse response = readCache(profile.getStudentId())
                .orElseGet(() -> ruleFallback(profileId, userId));
        return withFreshPersona(response, profileId);
    }

    /**
     * POST /recommendations — AI 추천 버튼.
     * 유저 입력 데이터(수강·요건·인증·프로필)의 지문(fingerprint)을 지난 생성 시점과 비교해서
     *  - 변화 없음 → 캐시 그대로(같은 결과 유지, AI 미호출)
     *  - 하나라도 변화 → passport-ai를 라이브 실행해 최신 데이터 기반으로 5방향 전체 재생성
     * 브릿지 실패 시 안전망: 기존 캐시 → 규칙 폴백 (발표 사고 방지, 기존 동작 유지).
     */
    public RecommendationResponse generate(Long profileId, Long userId) {
        Profile profile = profileService.findOwnedProfile(profileId, userId);
        String studentId = profile.getStudentId();

        String currentFp = fingerprint(profile);
        Optional<String> storedFp = readFingerprint(studentId);
        Optional<RecommendationResponse> cached = readCache(studentId);

        if (cached.isPresent() && storedFp.isEmpty()) {
            // 지문 도입 이전에 만들어진 캐시(예: 사전 생성 데모) — 캐시 유지 + 지문만 기록.
            // 이후 데이터가 실제로 바뀌면 그때부터 라이브 재생성이 동작한다.
            writeFingerprint(studentId, currentFp);
            return withFreshPersona(cached.get(), profileId);
        }
        if (cached.isPresent() && currentFp.equals(storedFp.get())) {
            return withFreshPersona(cached.get(), profileId);   // 데이터 변화 없음 → 같은 결과 유지
        }

        // 데이터 변경(또는 첫 생성) → 라이브 재생성. bridge.py가 결과를 cache/{studentId}.json에 저장한다.
        Optional<RecommendationResponse> fresh = passportAiBridge.generate(buildPayload(profile, profileId, userId));
        if (fresh.isPresent()) {
            // live일 때만 지문 저장 → AI가 일시 실패(폴백)했으면 다음 클릭에서 자동 재시도
            if ("live".equals(fresh.get().source())) {
                writeFingerprint(studentId, currentFp);
            }
            return withFreshPersona(fresh.get(), profileId);
        }

        log.warn("passport-ai 라이브 재생성 실패 — 기존 캐시/규칙 폴백으로 응답 (profileId={})", profileId);
        return withFreshPersona(cached.orElseGet(() -> ruleFallback(profileId, userId)), profileId);
    }

    /**
     * persona는 수강 이력이 바뀔 때마다 CourseService→PersonaService가 이미 재계산해 DB에 저장해둔 값이 최신이다.
     * passport-ai 캐시/폴백 응답에 담긴 persona(생성 시점 스냅샷)보다 이 DB 값을 우선해, "AI 분석하기"를 누르지
     * 않고 성적만 새로 입력해도 홈·AI분석 화면의 persona가 항상 최신 성적을 반영하도록 한다(원석 요청).
     */
    private RecommendationResponse withFreshPersona(RecommendationResponse response, Long profileId) {
        PersonaDto fresh = personaService.get(profileId).orElse(response.persona());
        if (fresh == response.persona()) {
            return response;
        }
        return new RecommendationResponse(response.directions(), response.recommendations(),
                response.defaultDirectionId(), response.source(), response.generatedAt(), fresh);
    }

    // ── passport-ai payload ──────────────────────────────────────────────

    /**
     * bridge.py에 넘길 유저 데이터. 사실 판정(부족학점·GPA)은 전부 규칙 기반 진단 결과를 그대로 쓴다(원칙 1).
     * lackingAreas는 빈 배열 — Spring 진단에 핵심교양 과목↔영역 매핑 데이터가 없어 계산 불가(문서 51 §7 한계).
     */
    private Map<String, Object> buildPayload(Profile profile, Long profileId, Long userId) {
        DiagnosisResponse diagnosis = diagnosisService.diagnose(profileId, userId);

        Map<String, Double> shortCredits = new LinkedHashMap<>();
        diagnosis.categories().stream()
                .filter(c -> c.shortfall() > 0)
                .forEach(c -> shortCredits.put(c.category().name(), c.shortfall()));

        List<Map<String, Object>> history = courseRepository.findAllByProfileId(profileId).stream()
                .<Map<String, Object>>map(c -> Map.of(
                        "courseName", c.getName(),
                        "grade", c.getGrade().getLabel()))
                .toList();

        Map<String, Object> diagnosisPart = new LinkedHashMap<>();
        diagnosisPart.put("lackingAreas", List.of());
        diagnosisPart.put("shortCredits", shortCredits);
        diagnosisPart.put("gpa", diagnosis.gpa().current());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("studentKey", profile.getStudentId());
        payload.put("diagnosis", diagnosisPart);
        payload.put("history", history);
        return payload;
    }

    // ── 유저 데이터 지문 (변경 감지) ─────────────────────────────────────

    /**
     * 추천에 영향을 주는 유저 입력 전체(프로필·수강·인증·졸업요건)를 정규화 문자열로 만들어 SHA-256 해시.
     * 어느 값 하나라도 바뀌면 지문이 달라진다 = "데이터가 변경됐다".
     */
    private String fingerprint(Profile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("profile:").append(profile.getDeptCode()).append('|')
                .append(profile.getStudentId()).append('|')
                .append(profile.getAdmissionYear()).append('\n');

        courseRepository.findAllByProfileId(profile.getId()).stream()
                .sorted(Comparator.comparing(Course::getId))
                .forEach(c -> sb.append("course:")
                        .append(c.getName()).append('|').append(c.getCredit()).append('|')
                        .append(c.getCategory()).append('|').append(c.getGrade()).append('|')
                        .append(c.getYear()).append('|').append(c.getSemester()).append('|')
                        .append(c.isRetake()).append('\n'));

        certificationRepository.findAllByProfileId(profile.getId()).stream()
                .sorted(Comparator.comparing(c -> c.getType().name()))
                .forEach(c -> sb.append("cert:")
                        .append(c.getType()).append('|').append(c.getStatus()).append('\n'));

        userRequirementRepository.findByProfileId(profile.getId()).ifPresent(req -> {
            RequirementCredits cr = req.getCredits();
            if (cr != null) {
                sb.append("reqCredits:")
                        .append(cr.getTotal()).append('|').append(cr.getMajorRequired()).append('|')
                        .append(cr.getMajorElective()).append('|').append(cr.getMajorBasic()).append('|')
                        .append(cr.getLiberalRequired()).append('|').append(cr.getLiberalElective()).append('|')
                        .append(cr.getMajorTotal()).append('|').append(cr.getLiberalTotal()).append('|')
                        .append(cr.getTeaching()).append('|').append(cr.getGeneralElective()).append('|')
                        .append(cr.getExternal()).append('|').append(cr.getDoubleMajor()).append('|')
                        .append(cr.getMinor()).append('|').append(cr.getLinkedMajor()).append('|')
                        .append(cr.getConvergenceMajor()).append('\n');
            }
            sb.append("reqType:").append(req.getRequiredCourseType()).append('|')
                    .append(req.getGraduationExam()).append('|').append(req.isDraft()).append('\n');
            for (CoreLiberalArea area : req.getCoreLiberal()) {
                sb.append("reqArea:").append(area.getAreaNo()).append('|').append(area.getAreaName()).append('|')
                        .append(area.getCourseCount()).append('|').append(area.getCredit()).append('|')
                        .append(area.isTarget()).append('\n');
            }
            RequirementCertificationTargets ct = req.getCertification();
            if (ct != null) {
                sb.append("reqCert:")
                        .append(ct.getForeignLangCert()).append('|').append(ct.getInfoProcessing()).append('|')
                        .append(ct.getCpr()).append('|').append(ct.getSocialService()).append('|')
                        .append(ct.getForeignLangExtra()).append('\n');
            }
        });

        return sha256(sb.toString());
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 사용 불가", e);
        }
    }

    private Path fingerprintPath(String studentId) {
        return Path.of(cacheDir, sanitize(studentId) + ".fp");
    }

    private Optional<String> readFingerprint(String studentId) {
        Path path = fingerprintPath(studentId);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8).strip());
        } catch (IOException e) {
            log.warn("지문 파일 읽기 실패 (path={}): {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeFingerprint(String studentId, String fingerprint) {
        Path path = fingerprintPath(studentId);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, fingerprint, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("지문 파일 저장 실패 (path={}): {}", path, e.getMessage());
        }
    }

    // ── 캐시 서빙 / 규칙 폴백 (기존 동작) ────────────────────────────────

    private Optional<RecommendationResponse> readCache(String studentId) {
        Path path = Path.of(cacheDir, sanitize(studentId) + ".json");
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), RecommendationResponse.class));
        } catch (IOException e) {
            log.warn("추천 캐시 파싱 실패 (studentId={}, path={}): {}", studentId, path, e.getMessage());
            return Optional.empty();
        }
    }

    /** passport-ai core/cache.py의 파일명 규칙(영숫자/-/_ 만 허용)과 동일하게 경로 조작 문자를 제거한다. */
    private String sanitize(String studentId) {
        return studentId.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    /**
     * passport-ai 캐시가 없을 때의 최후 폴백.
     * ⚠️ Spring에는 개설과목 카탈로그가 없어 실제 과목을 추천할 근거가 없다.
     * 따라서 과목을 지어내지 않고, 진단(diagnosis)에서 확인된 부족 이수구분 top5를 그대로 안내한다.
     * 실제 과목 단위 추천은 passport-ai 캐시가 채워진 뒤에만 제공된다.
     */
    private RecommendationResponse ruleFallback(Long profileId, Long userId) {
        DiagnosisResponse diagnosis = diagnosisService.diagnose(profileId, userId);

        List<RecoItemDto> items = diagnosis.categories().stream()
                .filter(c -> c.shortfall() > 0)
                .sorted(Comparator.comparingDouble(CategoryProgress::shortfall).reversed())
                .limit(5)
                .map(this::toShortfallItem)
                .toList();

        DirectionDto direction = new DirectionDto(
                "FAST_GRAD", "졸업요건 집중형",
                "부족한 졸업 요건부터 확실하게 채우는 전략이에요.",
                null, true, items);

        // persona: 규칙 폴백은 passport-ai의 성향 분석(과목 특성 데이터)을 거치지 않아 생성 근거가 없다 — null(FE 미표시).
        return new RecommendationResponse(
                List.of(direction), items, "FAST_GRAD", "fallback",
                Instant.now().toString(), null);
    }

    /** 학점은 0.5 단위라 double이지만 화면 문구에 "12.0학점"으로 나가면 안 된다. 정수면 소수점을 떼고, 아니면 12.5로 남긴다. */
    private static String formatCredit(double credit) {
        return credit == Math.rint(credit) ? String.valueOf((long) credit) : String.valueOf(credit);
    }

    private RecoItemDto toShortfallItem(CategoryProgress category) {
        String label = categoryLabel(category.category());
        return new RecoItemDto(
                null,
                label + " " + formatCredit(category.shortfall()) + "학점 이수 필요",
                category.shortfall(),
                label,
                List.of(
                        "진단 결과 " + label + " 학점이 부족해요.",
                        "구체적인 과목 추천은 AI 추천 캐시가 준비되면 제공돼요.",
                        "부족 학점을 채우면 졸업 요건 충족에 가까워져요."
                ));
    }

    private String categoryLabel(CourseCategory category) {
        return category.getKoreanLabel();
    }
}
