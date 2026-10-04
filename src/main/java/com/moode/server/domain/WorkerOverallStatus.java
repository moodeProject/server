package com.moode.server.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 작업자 관리 화면(목록/상세)의 종합 상태 배지. 저장 컬럼이 아니라 최신 센서값(fallState/healthState)과
 * 미해결 외부요인(HazardEvent category=EXTERNAL) 을 조회 시점에 조합해 계산한다.
 * WorkerServiceImpl.toHeatRiskResponse() 의 조합 방식과 같은 패턴이다.
 */
@Getter
@RequiredArgsConstructor
public enum WorkerOverallStatus {
    NORMAL("정상"),
    CAUTION("주의"),
    DANGER("위험");

    private final String label;
}
