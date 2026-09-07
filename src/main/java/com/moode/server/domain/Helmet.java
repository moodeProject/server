package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 스마트 헬멧. ERD 기준.
 *
 * <p>AI 서버가 보내는 helmetId("H-001")는 여기의 helmetNo 다.
 * 기존 SensorData 가 쓰는 문자열 식별자는 deviceId 쪽에 대응한다.
 */
@Entity
@Table(
        name = "helmet",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_helmet_helmet_no", columnNames = "helmet_no"),
                @UniqueConstraint(name = "uk_helmet_device_id", columnNames = "device_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Helmet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "helmet_id")
    private Long id;

    @Column(name = "helmet_no", nullable = false, length = 20)
    private String helmetNo;

    @Column(name = "device_id", nullable = false, length = 50)
    private String deviceId;

    // 현재 배정된 작업자. 미배정이면 null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", foreignKey = @ForeignKey(name = "fk_helmet_worker"))
    private Worker worker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private SensorStatus sensorStatus = SensorStatus.DISCONNECTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private OperationalStatus operationalStatus = OperationalStatus.STANDBY;

    private LocalDateTime lastCommunicationAt;

    @Column(length = 255)
    private String apiKeyHash;

    @Column(nullable = false)
    private LocalDateTime registeredAt;

    @PrePersist
    public void prePersist() {
        if (this.registeredAt == null) {
            this.registeredAt = LocalDateTime.now();
        }
    }

    /** 센서 데이터를 실제로 수신했을 때 통신 상태를 갱신한다. operationalStatus(관리자 배정 상태)는 건드리지 않는다. */
    public void recordCommunication() {
        this.sensorStatus = SensorStatus.CONNECTED;
        this.lastCommunicationAt = LocalDateTime.now();
    }
}
