package com.moode.server.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SensorDataRequest {

    private String deviceId;

    private Double ax;
    private Double ay;
    private Double az;
    private Double gx;
    private Double gy;
    private Double gz;

    // MAX30102 계산 결과
    private Double heartRate;
    private Double spo2;

    @Schema(description = "펌웨어 자세 판정 결과",
            example = "STABLE",
            allowableValues = {"STABLE", "STUMBLE", "COLLAPSE"})
    private String posture;
    private Boolean healthAbnormal;  // checkHealthAbnormal() 결과
    private String level;            // NORMAL / RECOMMEND / ACTION_REQUIRED

    private Double hrv;               // RMSSD(ms). 계산 불가 시 음수/누락
    private Boolean fatigueAbnormal;  // 개인 HRV 기준선 대비 이탈 여부
    private Boolean heatRiskAbnormal; // 활동량 대비 심박 지속 상승 여부 (외부 기상 정보 미반영 상태의 1차 지표)
}
