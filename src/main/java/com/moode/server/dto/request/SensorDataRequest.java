package com.moode.server.dto.request;

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

    private String posture;          // STABLE / STUMBLE / COLLAPSE
    private Boolean healthAbnormal;  // checkHealthAbnormal() 결과
    private String level;            // NORMAL / RECOMMEND / ACTION_REQUIRED
}
