package com.passport.profile.domain;

import com.passport.auth.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Builder
    public Profile(User user, String deptCode, String studentId, int admissionYear, String name) {
        this.user = user;
        this.deptCode = deptCode;
        this.studentId = studentId;
        this.admissionYear = admissionYear;
        this.name = name;
    }

    public void update(String deptCode, String studentId, int admissionYear, String name) {
        this.deptCode = deptCode;
        this.studentId = studentId;
        this.admissionYear = admissionYear;
        this.name = name;
    }
}
