package com.passport.requirement.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 졸업 인증 5분야(사진1 §4)의 대상 여부. 학생 개인의 합격 판정(certification 도메인)과는 다른 개념이다. */
public enum CertMark {
    TARGET("대상"),
    NOT_TARGET("비대상"),
    DONE("완료");

    private final String label;

    CertMark(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static CertMark fromLabel(String label) {
        for (CertMark mark : values()) {
            if (mark.label.equals(label)) {
                return mark;
            }
        }
        throw new IllegalArgumentException("알 수 없는 인증 대상 표기입니다: " + label);
    }
}
