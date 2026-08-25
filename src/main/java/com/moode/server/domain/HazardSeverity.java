package com.moode.server.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 위험 등급. AI 서버는 이 값을 보내지 않고, 서버가 hazardType 으로 판정한다.
 * 판정 매핑은 {@link com.moode.server.config.HazardSeverityPolicy} 참고.
 *
 * <p>심각도 비교는 ordinal() 이 아니라 level 로 한다. 나중에 중간 등급(예: CAUTION)이
 * 끼어들어도 비교 로직이 조용히 깨지지 않게 하기 위함이다.
 */
@Getter
@RequiredArgsConstructor
public enum HazardSeverity {

    WARNING(10, "주의"),
    DANGER(20, "위험");

    private final int level;

    /** 화면에 그대로 표시할 한글 라벨. 색상 분기는 라벨이 아니라 enum 값으로 해야 한다. */
    private final String label;

    public boolean isAtLeast(HazardSeverity other) {
        return this.level >= other.level;
    }
}
