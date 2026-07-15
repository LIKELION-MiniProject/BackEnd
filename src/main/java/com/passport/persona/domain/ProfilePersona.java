package com.passport.persona.domain;

import com.passport.profile.domain.Profile;
import com.passport.recommendation.dto.PersonaDto;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 학습 성향(persona) — 수강 이력(성적)이 바뀔 때마다 PersonaService가 규칙 기반으로 재계산해 저장한다(원석 요청).
 * 프로필 1:1. 홈 대시보드 + AI 추천 화면이 공유해서 읽는다 — 매 조회마다 재계산하지 않고 이 저장값을 서빙한다.
 */
@Entity
@Table(name = "profile_personas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfilePersona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false, unique = true)
    private Profile profile;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, length = 1000)
    private String description;

    @ElementCollection
    @CollectionTable(name = "profile_persona_strategies", joinColumns = @JoinColumn(name = "profile_persona_id"))
    @OrderColumn(name = "position")
    @Column(name = "strategy", length = 500)
    private List<String> strategies = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_persona_summary", joinColumns = @JoinColumn(name = "profile_persona_id"))
    @OrderColumn(name = "position")
    @Column(name = "summary_line", length = 500)
    private List<String> summary = new ArrayList<>();

    private LocalDateTime updatedAt;

    @Builder
    public ProfilePersona(Profile profile, String type, String label, String description,
                          List<String> strategies, List<String> summary) {
        this.profile = profile;
        this.type = type;
        this.label = label;
        this.description = description;
        this.strategies = strategies != null ? new ArrayList<>(strategies) : new ArrayList<>();
        this.summary = summary != null ? new ArrayList<>(summary) : new ArrayList<>();
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String type, String label, String description, List<String> strategies, List<String> summary) {
        this.type = type;
        this.label = label;
        this.description = description;
        this.strategies.clear();
        if (strategies != null) {
            this.strategies.addAll(strategies);
        }
        this.summary.clear();
        if (summary != null) {
            this.summary.addAll(summary);
        }
        this.updatedAt = LocalDateTime.now();
    }

    public PersonaDto toDto() {
        return new PersonaDto(type, label, description, List.copyOf(strategies), List.copyOf(summary));
    }
}
