package com.passport.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.passport.auth.domain.User;
import jakarta.persistence.Column;
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

@Entity
@Table(name = "profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** 요건 매칭 키 (예: 빅데이터 인공지능 전공 코드) */
    @Column(nullable = false)
    private String deptCode;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private int admissionYear;

    private String name;

    // ↓ 마이페이지 표시 전용 필드(신영 UI 정합, PHASE1-①). 진단·요건 로직에는 연결하지 않는다.
    // 전부 nullable — 값이 없으면 FE가 "-"로 표시.
    /** 학년 */
    private Integer grade;
    /** 이수학기(1 또는 2) */
    private Integer currentSemester;
    @Enumerated(EnumType.STRING)
    private EnrollmentStatus enrollmentStatus;
    /** 졸업 예정 연도 */
    private Integer expectedGraduationYear;
    @Enumerated(EnumType.STRING)
    private DoubleMajorType doubleMajorType;
    /** 추가 전공명 — 복수전공/융복합전공일 때만 의미 있음 */
    private String additionalMajor;
    /** 지도교수명 */
    private String advisorProfessor;

    @Builder
    public Profile(User user, String deptCode, String studentId, int admissionYear, String name,
                   Integer grade, Integer currentSemester, EnrollmentStatus enrollmentStatus,
                   Integer expectedGraduationYear, DoubleMajorType doubleMajorType,
                   String additionalMajor, String advisorProfessor) {
        this.user = user;
        this.deptCode = deptCode;
        this.studentId = studentId;
        this.admissionYear = admissionYear;
        this.name = name;
        this.grade = grade;
        this.currentSemester = currentSemester;
        this.enrollmentStatus = enrollmentStatus;
        this.expectedGraduationYear = expectedGraduationYear;
        this.doubleMajorType = doubleMajorType;
        this.additionalMajor = additionalMajor;
        this.advisorProfessor = advisorProfessor;
    }

    public void update(String deptCode, String studentId, int admissionYear, String name,
                        Integer grade, Integer currentSemester, EnrollmentStatus enrollmentStatus,
                        Integer expectedGraduationYear, DoubleMajorType doubleMajorType,
                        String additionalMajor, String advisorProfessor) {
        this.deptCode = deptCode;
        this.studentId = studentId;
        this.admissionYear = admissionYear;
        this.name = name;
        this.grade = grade;
        this.currentSemester = currentSemester;
        this.enrollmentStatus = enrollmentStatus;
        this.expectedGraduationYear = expectedGraduationYear;
        this.doubleMajorType = doubleMajorType;
        this.additionalMajor = additionalMajor;
        this.advisorProfessor = advisorProfessor;
    }

    /** 재학 여부(마이페이지 표시 전용) */
    public enum EnrollmentStatus {
        ENROLLED("재학생"), ON_LEAVE("휴학생"), EXPECTED_GRADUATION("졸업 예정");

        private final String label;

        EnrollmentStatus(String label) {
            this.label = label;
        }

        @JsonValue
        public String getLabel() {
            return label;
        }

        @JsonCreator
        public static EnrollmentStatus fromLabel(String label) {
            for (EnrollmentStatus status : values()) {
                if (status.label.equals(label)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("알 수 없는 재학 여부 표기입니다: " + label);
        }
    }

    /** 복수전공·융복합전공 여부(마이페이지 표시 전용) */
    public enum DoubleMajorType {
        NONE("해당 없음"), DOUBLE_MAJOR("복수전공"), CONVERGENCE_MAJOR("융복합전공");

        private final String label;

        DoubleMajorType(String label) {
            this.label = label;
        }

        @JsonValue
        public String getLabel() {
            return label;
        }

        @JsonCreator
        public static DoubleMajorType fromLabel(String label) {
            for (DoubleMajorType type : values()) {
                if (type.label.equals(label)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("알 수 없는 복수전공 여부 표기입니다: " + label);
        }
    }
}
