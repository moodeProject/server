package com.moode.server.dto.response;

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
}
