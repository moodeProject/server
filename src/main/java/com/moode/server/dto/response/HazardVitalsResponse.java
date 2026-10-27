package com.moode.server.dto.response;

import com.moode.server.domain.HazardEvent;
import com.moode.server.domain.WorkerState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 건강 이상 사고가 생긴 순간의 생체 값 스냅샷. 이후 센서 값이 바뀌어도 사고 기록은 그대로다.
 * 영상·추락 사고처럼 생체 값을 저장하지 않은 사고면 상세 응답에서 null 이다.
 */
@Getter
@Builder
public class HazardVitalsResponse {

    @Schema(description = "발생 당시 심박수(bpm). 측정 안 됐으면 null", example = "128.0", nullable = true)
    private Double heartRate;

    @Schema(description = "발생 당시 SpO₂(%). 측정 안 됐으면 null", example = "93.0", nullable = true)
    private Double spo2;

    @Schema(description = "발생 당시 HRV(RMSSD, ms). 측정 안 됐으면 null", example = "21.5", nullable = true)
    private Double hrv;

    @Schema(description = "발생 당시 건강 위험 단계", example = "ACTION_REQUIRED", nullable = true)
    private WorkerState healthState;

    @Schema(description = "근거가 된 sensor_data 행 id", example = "4914", nullable = true)
    private Long sensorDataId;

    /** 저장된 생체 값이 하나도 없으면 null 을 돌려준다. */
    public static HazardVitalsResponse from(HazardEvent event) {
        if (event.getHeartRate() == null && event.getSpo2() == null
                && event.getHrv() == null && event.getHealthState() == null) {
            return null;
        }
        return HazardVitalsResponse.builder()
                .heartRate(event.getHeartRate())
                .spo2(event.getSpo2())
                .hrv(event.getHrv())
                .healthState(event.getHealthState())
                .sensorDataId(event.getSensorDataId())
                .build();
    }
}
