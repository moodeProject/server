package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 증거 영상 클립. HazardEvent 와 1:0..1.
 *
 * <p>AI 서버가 이벤트를 2번에 나눠 보내기 때문에(위험 확정 시 1번, 클립 완성 시 1번)
 * "이벤트는 있는데 클립은 아직 없는" 상태가 정상적으로 존재한다. 그래서 hazard_event 에
 * 인라인하지 않고 분리했고, hazard_event_id 에 UNIQUE 를 걸어 1:1 을 강제한다.
 *
 * <p><b>공개 URL 을 저장하지 않는다.</b> 버킷은 비공개로 두고 bucket + objectKey 만 보관한 뒤
 * 조회 시점에 pre-signed GET URL 을 발급한다. URL 을 컬럼에 저장하면 만료 관리를 할 수 없다.
 */
@Entity
@Table(
        name = "hazard_clip",
        uniqueConstraints = @UniqueConstraint(name = "uk_hazard_clip_hazard_event_id", columnNames = "hazard_event_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class HazardClip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "clip_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "hazard_event_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_hazard_clip_hazard_event")
    )
    private HazardEvent hazardEvent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private StorageType storageType;

    // LOCAL 저장인 경우 null.
    @Column(length = 64)
    private String bucket;

    // 예: clips/2026/08/25/H-001_083744.mp4 (공개 URL 이 아니라 객체 키)
    @Column(nullable = false, length = 512)
    private String objectKey;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal durationSeconds;

    @Column(nullable = false)
    private Integer frameCount;

    // 클립 안에서 '사고 시점' 마커 위치. AI 서버가 안 보내면 null.
    @Column(precision = 6, scale = 2)
    private BigDecimal markedOffsetSeconds;

    /**
     * objectKey 자리에 실수로 완성된 URL 이 들어오는 것을 막는다.
     * URL 을 저장해두면 pre-signed 만료 관리가 불가능해진다.
     */
    @PrePersist
    @PreUpdate
    public void validateObjectKey() {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 는 필수입니다.");
        }
        String lower = objectKey.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            throw new IllegalArgumentException("objectKey 에는 URL 이 아니라 객체 키만 저장해야 합니다. " + objectKey);
        }
    }
}
