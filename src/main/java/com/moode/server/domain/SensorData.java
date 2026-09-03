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

    private LocalDateTime recordedAt;

    @PrePersist
    public void prePersist() {
        this.recordedAt = LocalDateTime.now();
    }
}
