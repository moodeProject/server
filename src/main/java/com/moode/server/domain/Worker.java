package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 작업자. ERD 기준.
 */
@Entity
@Table(
        name = "worker",
        uniqueConstraints = @UniqueConstraint(name = "uk_worker_employee_no", columnNames = "employee_no")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "worker_id")
    private Long id;

    @Column(name = "employee_no", nullable = false, length = 20)
    private String employeeNo;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 50)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private WorkerStatus status = WorkerStatus.OFF_DUTY;

    // 소프트 삭제. hazard_event/work_log 등이 worker_id 를 참조하고 있어 hard delete 는
    // FK 위반으로 거의 항상 실패한다 — 목록/검색에서 숨기는 용도로 false 처리한다.
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // 현재 위치한 구역. 위험 이벤트 수신 시 zone 스냅샷의 출처가 된다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_zone_id", foreignKey = @ForeignKey(name = "fk_worker_current_zone"))
    private Zone currentZone;

    // 컬럼명을 명시한다. 기본 네이밍 전략은 이름 끝의 대문자 한 글자 앞에 밑줄을 넣지 않아
    // posX -> posx 가 되고, ERD/DDL 의 pos_x 와 어긋나 운영(ddl-auto=validate)에서 기동이 깨진다.
    @Column(name = "pos_x", precision = 8, scale = 2)
    private BigDecimal posX;

    @Column(name = "pos_y", precision = 8, scale = 2)
    private BigDecimal posY;

    @Column(length = 30)
    private String nearestBeaconId;

    private Short rssi;

    private LocalDateTime positionUpdatedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 헬멧이 보낸 BLE 비콘 스캔 결과로 현재 구역/근접 비콘을 갱신한다.
     * 좌표(posX/posY)는 이 경로에서 다루지 않는다 — 삼변측량 등 다른 위치추정이 생기면 별도로 채운다.
     *
     * @param zone            새로 판정된 구역. 알 수 없으면(구역 못 잡음) null.
     * @param nearestBeaconId 가장 신호가 센 비콘 식별자. 알 수 없으면 null.
     * @param rssi            그 비콘의 RSSI. 알 수 없으면 null.
     */
    public void updateBeaconLocation(Zone zone, String nearestBeaconId, Short rssi) {
        this.currentZone = zone;
        this.nearestBeaconId = nearestBeaconId;
        this.rssi = rssi;
        this.positionUpdatedAt = LocalDateTime.now();
    }

    // ===== 근태(출퇴근) 연동. AttendanceServiceImpl 에서만 호출한다. =====
    // (2026-10, 근태 API 작업 중 추가 — 주아연님 작업자 파트와 겹치는 파일이라 공유 필요)

    /** 출근 처리 시 작업 상태를 WORKING 으로 바꾼다. */
    public void startWork() {
        this.status = WorkerStatus.WORKING;
    }

    /** 퇴근 처리 시 작업 상태를 OFF_DUTY 로 바꾼다. */
    public void endWork() {
        this.status = WorkerStatus.OFF_DUTY;
    }

    // ===== 작업자 관리(CRUD). WorkerManagementServiceImpl 에서만 호출한다. =====

    public void updateProfile(String name, Zone zone) {
        this.name = name;
        this.currentZone = zone;
    }

    /** 소프트 삭제. 이미 비활성이어도 멱등하게 다시 호출 가능하다. */
    public void deactivate() {
        this.active = false;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
