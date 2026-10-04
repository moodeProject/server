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
                @UniqueConstraint(name = "uk_helmet_device_id", columnNames = "device_id"),
                // MySQL InnoDB 는 NULL 끼리 중복으로 안 쳐서 미배정(worker_id=NULL) 헬멧은 여러 개 허용된다.
                @UniqueConstraint(name = "uk_helmet_worker_id", columnNames = "worker_id")
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

    @Column(length = 255)
    private String memo;

    // 소프트 삭제. hazard_event 등이 helmet_id 를 참조하고 있어 hard delete 는 FK 위반으로 거의 항상 실패한다.
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

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

    // ===== 헬멧 관리(CRUD)/배정. HelmetManagementServiceImpl 에서만 호출한다. =====

    /** @throws IllegalStateException 이미 다른 작업자에게 배정된 헬멧일 때 (호출 전에 검증하는 것이 호출자의 책임이다) */
    public void assignTo(Worker worker) {
        this.worker = worker;
        this.operationalStatus = OperationalStatus.IN_USE;
    }

    public void unassign() {
        this.worker = null;
        this.operationalStatus = OperationalStatus.STANDBY;
    }

    public boolean isAssigned() {
        return this.worker != null;
    }

    public void updateMemo(String memo) {
        this.memo = memo;
    }

    /** 소프트 삭제. 배정 해제까지 함께 처리하는 것은 호출자(서비스)의 책임이다. */
    public void deactivate() {
        this.active = false;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
