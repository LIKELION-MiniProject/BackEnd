package com.passport.requirement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 핵심교양 5영역 중 1개 행(사진1 §3). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoreLiberalArea {

    @Column(nullable = false)
    private int areaNo;
    /** 임시저장(draft) 시 비어 있을 수 있어 nullable */
    private String areaName;
    @Column(nullable = false)
    private int courseCount;
    @Column(nullable = false)
    private int credit;
    @Column(nullable = false)
    private boolean target;

    @Builder
    public CoreLiberalArea(int areaNo, String areaName, int courseCount, int credit, boolean target) {
        this.areaNo = areaNo;
        this.areaName = areaName;
        this.courseCount = courseCount;
        this.credit = credit;
        this.target = target;
    }
}
