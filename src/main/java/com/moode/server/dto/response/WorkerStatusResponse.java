package com.moode.server.dto.response;

import com.moode.server.domain.WorkerState;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkerStatusResponse {
    private String deviceId;

    // 추락 / 건강, 서로 독립적인 판단 결과
    private WorkerState fallState;
    private WorkerState healthState;

    private LocalDateTime recordedAt;

    private Double ax;
    private Double ay;
    private Double az;
    private Double gx;
    private Double gy;
    private Double gz;

    private Double heartRate;
    private Double spo2;

    private Double fallConfidence;
}
