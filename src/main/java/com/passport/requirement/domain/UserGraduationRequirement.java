package com.passport.requirement.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.passport.profile.domain.Profile;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 유저가 입력한 졸업 요건 기준(사진1). 프로필당 1건.
 * 저장값이 없으면 DiagnosisService는 학과 하드코딩(BigdataAiRequirement)으로 폴백한다 — RequirementResolutionService 참고.
 */
@Entity
@Table(name = "user_graduation_requirements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGraduationRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false, unique = true)
    private Profile profile;

    @Embedded
    private RequirementCredits credits;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_course_type")
    private RequiredCourseType requiredCourseType;

    @ElementCollection
    @CollectionTable(name = "user_requirement_core_liberal", joinColumns = @JoinColumn(name = "user_requirement_id"))
    private List<CoreLiberalArea> coreLiberal = new ArrayList<>();

    @Embedded
    private RequirementCertificationTargets certification;

    @Enumerated(EnumType.STRING)
    @Column(name = "graduation_exam")
    private GraduationExamType graduationExam;

    /** 임시저장 여부 — true면 유효성 검증을 완화해 저장된 값 */
    @Column(nullable = false)
    private boolean draft;

    @Builder
    public UserGraduationRequirement(Profile profile, RequirementCredits credits, RequiredCourseType requiredCourseType,
                                      List<CoreLiberalArea> coreLiberal, RequirementCertificationTargets certification,
                                      GraduationExamType graduationExam, boolean draft) {
        this.profile = profile;
        this.credits = credits;
        this.requiredCourseType = requiredCourseType;
        this.coreLiberal = coreLiberal != null ? new ArrayList<>(coreLiberal) : new ArrayList<>();
        this.certification = certification;
        this.graduationExam = graduationExam;
        this.draft = draft;
    }

    public void update(RequirementCredits credits, RequiredCourseType requiredCourseType, List<CoreLiberalArea> coreLiberal,
                        RequirementCertificationTargets certification, GraduationExamType graduationExam, boolean draft) {
        this.credits = credits;
        this.requiredCourseType = requiredCourseType;
        this.coreLiberal.clear();
        if (coreLiberal != null) {
            this.coreLiberal.addAll(coreLiberal);
        }
        this.certification = certification;
        this.graduationExam = graduationExam;
        this.draft = draft;
    }

    /** 필수 이수구분(사진1 상단) — "교필"·"전필" 한글 표기로 직렬화(Course.Grade와 동일한 라벨 패턴) */
    public enum RequiredCourseType {
        GE_REQUIRED("교필"),
        MAJOR_REQUIRED("전필");

        private final String label;

        RequiredCourseType(String label) {
            this.label = label;
        }

        @JsonValue
        public String getLabel() {
            return label;
        }

        @JsonCreator
        public static RequiredCourseType fromLabel(String label) {
            for (RequiredCourseType type : values()) {
                if (type.label.equals(label)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("알 수 없는 이수구분 표기입니다: " + label);
        }
    }

    /** 졸업시험/논문 요건 유형 — JSON은 상수명 그대로(EXAM/EITHER/THESIS/NONE) 직렬화 */
    public enum GraduationExamType {
        EXAM, EITHER, THESIS, NONE
    }
}
