package com.passport.requirement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 유저가 입력한 졸업 요건의 학점 항목(사진1). 필드명은 FE 계약(부록 PART 3, liberalRequired/liberalElective 등)과 그대로 맞춘다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequirementCredits {

    @Column(nullable = false)
    private int total;
    @Column(nullable = false)
    private int majorRequired;
    @Column(nullable = false)
    private int majorElective;
    @Column(nullable = false)
    private int majorBasic;
    @Column(nullable = false)
    private int liberalRequired;
    @Column(nullable = false)
    private int liberalElective;
    @Column(nullable = false)
    private int majorTotal;
    @Column(nullable = false)
    private int liberalTotal;
    @Column(nullable = false)
    private int teaching;
    @Column(nullable = false)
    private int generalElective;
    @Column(nullable = false)
    private int external;
    @Column(nullable = false)
    private int doubleMajor;
    @Column(nullable = false)
    private int minor;
    @Column(nullable = false)
    private int linkedMajor;
    @Column(nullable = false)
    private int convergenceMajor;

    @Builder
    public RequirementCredits(int total, int majorRequired, int majorElective, int majorBasic,
                               int liberalRequired, int liberalElective, int majorTotal, int liberalTotal,
                               int teaching, int generalElective, int external, int doubleMajor,
                               int minor, int linkedMajor, int convergenceMajor) {
        this.total = total;
        this.majorRequired = majorRequired;
        this.majorElective = majorElective;
        this.majorBasic = majorBasic;
        this.liberalRequired = liberalRequired;
        this.liberalElective = liberalElective;
        this.majorTotal = majorTotal;
        this.liberalTotal = liberalTotal;
        this.teaching = teaching;
        this.generalElective = generalElective;
        this.external = external;
        this.doubleMajor = doubleMajor;
        this.minor = minor;
        this.linkedMajor = linkedMajor;
        this.convergenceMajor = convergenceMajor;
    }
}
