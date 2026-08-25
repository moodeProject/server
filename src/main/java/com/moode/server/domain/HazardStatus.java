package com.moode.server.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 조치 상태.
 *
 * <p>ERD 의 process_status 는 UNPROCESSED / REST_RECOMMENDED 를 포함하지만,
 * 프론트 명세가 UNHANDLED / IN_PROGRESS / RESOLVED 세 가지로 합의되어 있어 그쪽을 따른다.
 */
@Getter
@RequiredArgsConstructor
public enum HazardStatus {

    UNHANDLED("미처리"),
    IN_PROGRESS("처리중"),
    RESOLVED("확인완료");

    private final String label;
}
