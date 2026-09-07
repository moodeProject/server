package com.moode.server.domain;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_data")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    private Boolean healthAbnormal;

    // 추락 / 건강, 서로 독립적으로 판단해서 각자 저장
    @Enumerated(EnumType.STRING)
    private WorkerState fallState;

    @Enumerated(EnumType.STRING)
    private WorkerState healthState;

    // 추락 판단에 대한 AI 신뢰도. AI 호출 실패 시 null.
    private Double fallConfidence;

    // 센서가 보낸 자세 상태 원본. STABLE / STUMBLE / COLLAPSE
    private String posture;

    // 헬멧이 BLE 비콘 스캔으로 추정한 구역 (예: "ZONE-01"). zone 테이블과는 아직
    // FK로 연결되어 있지 않음 — 원본 문자열만 기록. 정식 연결은 별도 작업 필요.
    private String zoneId;

    private LocalDateTime recordedAt;

    @PrePersist
    public void prePersist() {
        this.recordedAt = LocalDateTime.now();
    }
}
