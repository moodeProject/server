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
}
