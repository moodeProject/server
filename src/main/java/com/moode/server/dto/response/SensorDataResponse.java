package com.moode.server.dto.response;

import com.moode.server.domain.WorkerState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SensorDataResponse {
    private Long id;
    private String deviceId;

    // 추락 / 건강, 서로 독립적인 판단 결과
    @Schema(description = "추락 판정 상태. NORMAL < ACTION_REQUIRED(휘청거림) < FALLING(자세 붕괴) < FALLEN(낙상) < UNCONSCIOUS(낙상 후 의식불명)", example = "NORMAL", nullable = true)
    private WorkerState fallState;
    private WorkerState healthState;

    private Double fallConfidence;

    // 둘 중 하나라도 경고 등급 이상이면 true
    private boolean alertTriggered;

    // 헬멧이 보낸 구역 추정값 원본 (zone 테이블과 아직 연결 안 됨)
    private String zoneId;

    private LocalDateTime recordedAt;
}
