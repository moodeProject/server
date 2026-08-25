package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * AI 서버가 보낸 메시지의 멱등 처리 로그.
 *
 * <p>AI 서버는 전송 실패 시 재시도하므로 같은 메시지가 두 번 올 수 있다.
 * idempotencyKey 를 PK 로 두고 저장이 성공하는지 여부로 중복을 막는다.
 * (조회 후 저장이 아니라 저장 시도 -> DataIntegrityViolationException 으로 판별해야 동시 수신에도 안전하다.)
 *
 * <p>eventUid 는 한 사건에 대해 여러 메시지(위험 확정 / 클립 완성)를 묶어보기 위한 것이라
 * UNIQUE 가 아니라 INDEX 다.
 *
 * <p>PK 를 직접 할당하기 때문에 Persistable 을 구현한다. 구현하지 않으면 Spring Data 가
 * "id 가 null 이 아니다 -> 기존 엔티티다"로 판단해 save() 를 merge(SELECT 후 UPDATE)로 처리하고,
 * 그러면 중복 메시지가 예외 없이 조용히 덮어써져 멱등성 보장이 통째로 무력화된다.
 */
@Entity
@Table(
        name = "inbound_message",
        indexes = @Index(name = "idx_inbound_message_event_uid", columnList = "event_uid")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class InboundMessage implements Persistable<String> {

    @Id
    @Column(name = "idempotency_key", columnDefinition = "CHAR(32)")
    private String idempotencyKey;

    @Column(name = "event_uid", nullable = false, columnDefinition = "CHAR(32)")
    private String eventUid;

    // 어떤 종류의 메시지였는지 (예: HAZARD_DETECTED / CLIP_UPLOADED)
    @Column(nullable = false, length = 24)
    private String eventType;

    @Column(nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime receivedAt;

    // 영속화된 적 없는 새 엔티티인지 표시. DB 컬럼이 아니다.
    @Transient
    @Builder.Default
    private boolean newEntity = true;

    @Override
    public String getId() {
        return idempotencyKey;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newEntity = false;
    }

    @PrePersist
    public void prePersist() {
        this.receivedAt = LocalDateTime.now();
    }
}
