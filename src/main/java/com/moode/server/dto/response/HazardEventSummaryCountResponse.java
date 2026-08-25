package com.moode.server.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 필터 탭의 건수 배지용. 현재 필터와 무관하게 항상 전체 기준 집계다.
 */
@Getter
@Builder
public class HazardEventSummaryCountResponse {

    private long all;
    private long external;
    private long health;
    private long fall;
}
