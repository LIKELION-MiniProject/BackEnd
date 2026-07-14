package com.passport.course.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.passport.profile.domain.Profile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int credit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Grade grade;

    /** DB 컬럼명은 year_taken — "year"는 H2 예약어라 테이블 생성이 실패해서 컬럼명만 바꿈. JSON 필드명은 그대로 year. */
    @Column(name = "year_taken", nullable = false)
    private int year;

    @Column(nullable = false)
    private int semester;

    @Column(nullable = false)
    private boolean retake;

    @Builder
    public Course(Profile profile, String name, int credit, CourseCategory category, Grade grade, int year, int semester, boolean retake) {
        this.profile = profile;
        this.name = name;
        this.credit = credit;
        this.category = category;
        this.grade = grade;
        this.year = year;
        this.semester = semester;
        this.retake = retake;
    }

    public void update(String name, int credit, CourseCategory category, Grade grade, int year, int semester, boolean retake) {
        this.name = name;
        this.credit = credit;
        this.category = category;
        this.grade = grade;
        this.year = year;
        this.semester = semester;
        this.retake = retake;
    }

    // 이수 구분
    public enum CourseCategory {
        MAJOR_BASIC("전공기초"),
        MAJOR_REQUIRED("전공필수"),
        MAJOR_ELECTIVE("전공선택"),
        GE_REQUIRED("교양필수"),
        GE_ELECTIVE("교양선택"),
        GENERAL_ELECTIVE("자유선택");

        private final String koreanLabel;

        CourseCategory(String koreanLabel) {
            this.koreanLabel = koreanLabel;
        }

        /** 화면 표기용 한글 라벨. JSON 직렬화(@JsonValue)는 그대로 상수명("MAJOR_BASIC" 등)을 쓴다 — 기존 계약 불변. */
        public String getKoreanLabel() {
            return koreanLabel;
        }
    }

    // 성적 (GPA 환산값 포함). P/NP는 GPA 계산에서 분자·분모 모두 제외
    public enum Grade {
        A_PLUS("A+", 4.5),
        A("A", 4.0),
        B_PLUS("B+", 3.5),
        B("B", 3.0),
        C_PLUS("C+", 2.5),
        C("C", 2.0),
        D_PLUS("D+", 1.5),
        D("D", 1.0),
        F("F", 0.0),
        P("P", null),
        NP("NP", null);

        private final String label;
        private final Double gpaValue;

        Grade(String label, Double gpaValue) {
            this.label = label;
            this.gpaValue = gpaValue;
        }

        @JsonValue
        public String getLabel() {
            return label;
        }

        public Double getGpaValue() {
            return gpaValue;
        }

        public boolean isGpaEligible() {
            return gpaValue != null;
        }

        /** F는 GPA에는 반영되지만(0점) 이수 학점으로는 인정되지 않음. NP도 학점 미인정. */
        public boolean isCreditEarned() {
            return this != F && this != NP;
        }

        @JsonCreator
        public static Grade fromLabel(String label) {
            for (Grade grade : values()) {
                if (grade.label.equals(label)) {
                    return grade;
                }
            }
            String alias = aliasToLabel(label);
            if (alias != null) {
                return fromLabel(alias);
            }
            throw new IllegalArgumentException("알 수 없는 성적 표기입니다: " + label);
        }

        /** "A0/B0/C0/D0" 표기(디자인·일부 학사 시스템 관행)를 정식 라벨("A/B/C/D")로 매핑 */
        private static String aliasToLabel(String label) {
            return switch (label) {
                case "A0" -> "A";
                case "B0" -> "B";
                case "C0" -> "C";
                case "D0" -> "D";
                default -> null;
            };
        }
    }
}
