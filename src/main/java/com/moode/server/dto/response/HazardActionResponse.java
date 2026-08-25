package com.moode.server.dto.response;

import com.moode.server.domain.HazardActionType;
import com.moode.server.domain.HazardStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 조치 실행 결과.
 *
 * <p>status 는 갱신된 값이라 프론트가 목록을 다시 조회하지 않고 이 값으로 화면을 바꾼다.
 * message 는 토스트에 그대로 쓰인다.
 */
@Getter
@Builder
public class HazardActionResponse {

    private Long eventId;
    private HazardActionType actionType;

    private HazardStatus status;
    private String statusLabel;

    private OffsetDateTime performedAt;

    // 알림이 도달한 인원 수
    private int notifiedCount;

    private String message;
}
