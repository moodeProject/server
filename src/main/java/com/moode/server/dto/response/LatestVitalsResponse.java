package com.moode.server.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 헬멧별 최근 "유효" 생체 측정값. 센서가 손가락을 못 잡거나 오류일 때 0 이 들어오므로
 * 값마다 0 보다 큰 마지막 측정을 따로 찾는다. 그래서 세 값의 측정 시각이 서로 다를 수 있다.
 * 한 번도 유효하게 측정된 적이 없는 값은 null 이다.
 */
@Getter
@Builder
public class LatestVitalsResponse {

    @Schema(description = "헬멧 deviceId", example = "HELMET-001")
    private String deviceId;

    @Schema(description = "마지막 유효 심박수(bpm)", example = "86.0", nullable = true)
    private Double heartRate;
    @Schema(description = "심박수 측정 시각", nullable = true)
    private LocalDateTime heartRateRecordedAt;

    @Schema(description = "마지막 유효 SpO₂(%)", example = "97.0", nullable = true)
    private Double spo2;
    @Schema(description = "SpO₂ 측정 시각", nullable = true)
    private LocalDateTime spo2RecordedAt;

    @Schema(description = "마지막 유효 HRV(RMSSD, ms)", example = "34.2", nullable = true)
    private Double hrv;
    @Schema(description = "HRV 측정 시각", nullable = true)
    private LocalDateTime hrvRecordedAt;

    @Schema(description = "가장 최근 수신 시각(값이 0 이었던 수신 포함). 센서가 살아 있는지 판단용", nullable = true)
    private LocalDateTime lastReceivedAt;
}
