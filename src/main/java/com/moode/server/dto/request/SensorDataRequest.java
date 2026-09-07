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

    @Schema(description = "헬멧이 BLE 비콘 스캔으로 추정한 현재 구역. "
            + "zone 테이블의 zone_code로 조회되어 Worker.currentZone에 반영됨 "
            + "(매핑되는 zone_code가 없으면 조용히 무시됨).",
            example = "ZONE-01",
            nullable = true)
    private String zoneId;
}
