package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 이상 감지 기록. AI 서버(FastAPI)가 영상에서 위험을 확정했을 때 1건 수신된다.
 * 증거 클립은 나중에 별도로 도착하므로 {@link HazardClip} 으로 분리되어 있다.
 *
 * <p>helmet / worker / zone 은 수신 시점의 배정을 확정해 기록하는 스냅샷이다.
 * 이후 헬멧이 재배정돼도 이 사건의 기록은 당시 배정을 유지해야 하므로 다시 조회하지 않는다.
 *
 * <p>handledBy 는 manager_account.admin_id 를 가리키지만 해당 엔티티가 아직 없어 스칼라로 둔다.
 */
@Entity
@Table(
        name = "hazard_event",
        indexes = {
                @Index(name = "idx_hazard_event_event_uid", columnList = "event_uid"),
                @Index(name = "idx_hazard_event_occurred_at", columnList = "occurred_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class HazardEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    // AI 서버가 만든 '사건' 식별자. 클립이 나중에 이 값으로 이벤트를 찾아온다.
    //
    // UNIQUE 가 아니라 일반 INDEX 다. 한 사건에서 위험이 연달아 감지되면(물웅덩이 -> 지게차)
    // AI 서버가 같은 eventUid + 다른 idempotencyKey 로 메시지를 여러 번 보내고,
    // 그 각각이 별도의 알림 대상이라 별도 행으로 남아야 하기 때문이다.
    // 중복 판정은 오직 inbound_message.idempotency_key 로만 한다.
    @Column(name = "event_uid", nullable = false, columnDefinition = "CHAR(32)")
    private String eventUid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "helmet_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_hazard_event_helmet"))
    private Helmet helmet;

    // 수신 시점의 헬멧 배정으로 확정해 기록하는 스냅샷. 미배정 헬멧이면 null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", foreignKey = @ForeignKey(name = "fk_hazard_event_worker"))
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", foreignKey = @ForeignKey(name = "fk_hazard_event_zone"))
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_category", nullable = false, length = 16)
    private HazardCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HazardType hazardType;

    // AI 서버가 아니라 서버가 hazardType 으로 판정한 값. HazardSeverityPolicy 참고.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HazardSeverity severity;

    // AI 신뢰도 원본값. 판정에 쓰지 않고 그대로 보존한다.
    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    // bbox 좌표의 기준 해상도. 프론트가 오버레이를 스케일링할 때 필요.
    private Integer frameWidth;

    private Integer frameHeight;

    // bbox 배열 원본 JSON. 프론트 오버레이용이라 서버는 파싱하지 않고 통과시킨다.
    @Column(columnDefinition = "TEXT")
    private String detectionsJson;

    // 목록 정렬 기준. 수신 시각이 아니라 AI 서버가 알려준 실제 발생 시각이다.
    @Column(nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HazardStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private HazardSource source = HazardSource.VISION_AI;

    // 목록/상세에 그대로 노출되는 상황 설명. 비어 있으면 hazardType 의 기본 문구를 쓴다.
    @Column(length = 255)
    private String description;

    @Column(length = 100)
    private String locationDetail;

    // ===== 아래는 사고 원인 분석 / AI 리포트 담당 영역. 영상 감지 경로에서는 채우지 않는다. =====

    @Column(length = 100)
    private String causePrimary;

    @Column(length = 100)
    private String causeSecondary;

    @Column(length = 200)
    private String causeContributing;

    @Column(length = 200)
    private String causeFinalEstimated;

    @Column(columnDefinition = "TEXT")
    private String aiReportText;

    // 추락/건강 사고가 어느 센서 수신 기록에서 만들어졌는지(sensor_data.id). 영상 사고는 null.
    private Long sensorDataId;

    // 건강 사고 발생 당시의 생체 값과 위험 단계 스냅샷. 다른 사고 종류는 null.
    private Double heartRate;
    private Double spo2;
    private Double hrv;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private WorkerState healthState;

    // 사건 발생 시각(occurredAt)과 달리 서버가 기록을 만든 시각이다.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // manager_account.admin_id. 조치 전에는 null.
    private Long handledBy;

    @Column(columnDefinition = "DATETIME(3)")
    private LocalDateTime handledAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = HazardStatus.UNHANDLED;
        }
        if (this.source == null) {
            this.source = HazardSource.VISION_AI;
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /**
     * 화면에 표시할 상황 설명. 저장된 값이 있으면 그것을, 없으면 위험 종류의 기본 문구를 쓴다.
     * (영상 감지 경로는 AI 가 설명을 보내지 않아 기본 문구로 채워진다)
     */
    public String resolveDescription() {
        return (description == null || description.isBlank())
                ? hazardType.getDescription()
                : description;
    }

    /**
     * 관리자 조치 상태를 변경한다.
     * UNHANDLED 로 되돌리는 경우 조치자/조치시각도 함께 비운다.
     */
    /**
     * 추락 중복 병합 시 더 심각한 단계로 올린다. 위험 종류와 심각도를 함께 바꾼다.
     */
    public void escalate(HazardType newType, HazardSeverity newSeverity) {
        this.hazardType = newType;
        this.severity = newSeverity;
    }

    public void handle(HazardStatus status, Long adminId) {
        if (status == null) {
            throw new IllegalArgumentException("변경할 상태는 필수입니다.");
        }
        this.status = status;
        if (status == HazardStatus.UNHANDLED) {
            this.handledBy = null;
            this.handledAt = null;
            return;
        }
        this.handledBy = adminId;
        this.handledAt = LocalDateTime.now();
    }
}
