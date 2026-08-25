package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 작업 구역. ERD 기준.
 *
 * <p>restrictedByAdminId 는 manager_account.admin_id 를 가리키지만 해당 엔티티가 아직 없어
 * 스칼라로 둔다. (hazard_event.handledBy 와 같은 이유)
 */
@Entity
@Table(
        name = "zone",
        uniqueConstraints = @UniqueConstraint(name = "uk_zone_zone_code", columnNames = "zone_code")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long id;

    @Column(name = "zone_code", nullable = false, length = 20)
    private String zoneCode;

    @Column(name = "zone_name", nullable = false, length = 50)
    private String zoneName;

    @Column(length = 20)
    private String floor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ZoneRiskLevel riskLevel = ZoneRiskLevel.LOW;

    // BLOCK_ZONE 조치가 켜는 플래그
    @Column(nullable = false)
    @Builder.Default
    private Boolean accessRestricted = false;

    // manager_account.admin_id
    private Long restrictedByAdminId;

    private LocalDateTime restrictedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /** 구역 접근 금지. 이미 금지 상태면 호출하지 않는 것이 호출자의 책임이다. */
    public void restrictAccess(Long adminId) {
        this.accessRestricted = true;
        this.restrictedByAdminId = adminId;
        this.restrictedAt = LocalDateTime.now();
    }

    public boolean isAccessRestricted() {
        return Boolean.TRUE.equals(accessRestricted);
    }
}
