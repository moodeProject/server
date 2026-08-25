package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 이벤트별 조치 실행 이력.
 *
 * <p>프론트 명세가 "이미 같은 조치를 실행함 -> 409"를 요구하는데, 그 판정에는
 * (이벤트, 조치종류) 단위 기록이 필요하다. hazard_event 의 status 만으로는
 * SEND_WARNING 을 이미 보냈는지 구분할 수 없다.
 *
 * <p>UNIQUE(hazard_event_id, action_type) 로 중복을 DB 레벨에서 막는다.
 * 동시에 두 번 눌려도 한 건만 성공한다.
 */
@Entity
@Table(
        name = "hazard_event_action",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hazard_event_action",
                columnNames = {"hazard_event_id", "action_type"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class HazardEventAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hazard_event_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_hazard_event_action_event"))
    private HazardEvent hazardEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private HazardActionType actionType;

    // manager_account.admin_id
    private Long performedBy;

    @Column(nullable = false)
    private LocalDateTime performedAt;

    private Integer notifiedCount;

    @PrePersist
    public void prePersist() {
        if (this.performedAt == null) {
            this.performedAt = LocalDateTime.now();
        }
    }
}
