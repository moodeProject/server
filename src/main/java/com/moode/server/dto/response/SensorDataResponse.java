package com.moode.server.dto.response;

import com.moode.server.domain.WorkerState;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SensorDataResponse {
    private Long id;
    private String deviceId;

    // 추락 / 건강, 서로 독립적인 판단 결과
    private WorkerState fallState;
    private WorkerState healthState;

    private Double fallConfidence;

    // 둘 중 하나라도 경고 등급 이상이면 true
    private boolean alertTriggered;

    private LocalDateTime recordedAt;
}
