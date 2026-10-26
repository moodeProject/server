package com.moode.server.dto.response;

import com.moode.server.domain.SensorData;
import com.moode.server.domain.WorkerState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class HeatRiskResponse {
    private String deviceId;
    private LocalDateTime recordedAt;

    private Double heartRate;
    private Double spo2;

    @Schema(description = "HRV(RMSSD, ms). 계산 불가/미수신 시 null", nullable = true)
    private Double hrv;

    @Schema(description = "개인 HRV 기준선 대비 이탈 여부 (피로도)")
    private Boolean fatigueAbnormal;

    @Schema(description = "몸 신호 1차 지표(활동량 대비 심박 지속 상승)")
    private Boolean heatRiskAbnormal;

    @Schema(description = "외부 기상(체감온도) 기반 위험 여부")
    private Boolean externalHeatWarning;

    @Schema(description = "몸 신호 + 외부 기상을 조합한 최종 온열질환 위험도")
    private WorkerState heatRiskLevel;

    /**
     * 몸 신호(heatRiskAbnormal) + 외부 기상(체감온도 캐시)을 조합해 최종 온열질환 위험도를 계산한다.
     * SensorDataServiceImpl.checkHeatRisk() 와 동일한 규칙.
     *
     * @param externalLevel WeatherHeatRiskCache.currentLevel()
     */
    public static HeatRiskResponse of(SensorData data, WorkerState externalLevel) {
        boolean bodySignal = Boolean.TRUE.equals(data.getHeatRiskAbnormal());
        boolean externalWarning = externalLevel.ordinal() >= WorkerState.RECOMMEND.ordinal();

        WorkerState heatRiskLevel;
        if (bodySignal && externalWarning) {
            heatRiskLevel = WorkerState.ACTION_REQUIRED;
        } else if (bodySignal || externalWarning) {
            heatRiskLevel = WorkerState.RECOMMEND;
        } else {
            heatRiskLevel = WorkerState.NORMAL;
        }

        return HeatRiskResponse.builder()
                .deviceId(data.getDeviceId())
                .recordedAt(data.getRecordedAt())
                .heartRate(data.getHeartRate())
                .spo2(data.getSpo2())
                .hrv(data.getHrv())
                .fatigueAbnormal(data.getFatigueAbnormal())
                .heatRiskAbnormal(data.getHeatRiskAbnormal())
                .externalHeatWarning(externalWarning)
                .heatRiskLevel(heatRiskLevel)
                .build();
    }
}
