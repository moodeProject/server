package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 근로자 SOS 요청.
 *
 * <p>누른 사람의 위치를 사고 위치로 본다. 본인보다 주변 동료가 신고하는 경우가 많고,
 * 그 경우 신고자 위치가 사고자 위치와 비슷하기 때문. 대상자는 따로 받지 않는다.
 *
 * <p>참조(작업자/헬멧/구역)가 삭제돼도 SOS 기록은 남도록 전부 ON DELETE SET NULL.
 * 누가 신고했는지는 requesterName / requesterEmployeeNo 로 남는다.
 * 테이블 DDL: resources/db/sos-schema.sql
 */
@Entity
@Table(name = "sos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Sos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sos_id")
    private Long id;

    // 누른 사람. 작업자가 삭제되면 null 이 된다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", foreignKey = @ForeignKey(name = "fk_sos_requester"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Worker requester;

    // 누른 순간의 이름·사번 (작업자 삭제 대비)
    @Column(name = "requester_name", nullable = false, length = 50)
    private String requesterName;

    @Column(name = "requester_employee_no", nullable = false, length = 20)
    private String requesterEmployeeNo;

    // 아래 3개는 누른 순간의 스냅샷. 이후 이동하거나 배정이 바뀌어도 기록은 그대로 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "helmet_id", foreignKey = @ForeignKey(name = "fk_sos_helmet"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Helmet helmet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", foreignKey = @ForeignKey(name = "fk_sos_zone"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Zone zone;

    @Column(name = "zone_updated_at")
    private LocalDateTime zoneUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private SosStatus status = SosStatus.REQUESTED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /** 처리 완료. 이미 완료된 건이면 false 를 돌려준다. */
    public boolean resolve() {
        if (this.status == SosStatus.RESOLVED) {
            return false;
        }
        this.status = SosStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        return true;
    }
}
