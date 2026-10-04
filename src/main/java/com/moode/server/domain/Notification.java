package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 알림. ERD 기준(notification).
 *
 * <p>sosId 는 sos_request 를 가리키지만 해당 테이블이 아직 없어(SOS 파트 작업 중) FK를 걸지
 * 않고 스칼라로 둔다. recipientAdminId / triggeredByAdminId 도 manager_account 가 아직 없어
 * 같은 이유로 스칼라다 ({@link HazardEvent#getHandledBy()} 와 동일한 패턴).
 * 두 테이블이 생기면 그때 FK를 추가한다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    // 위험 이벤트(hazard_event)가 삭제되면(테스트 cleanup, 데이터 정리 등) 거기에 달린
    // 알림도 같이 지워지도록 DB 레벨에서 CASCADE 를 건다 (Hibernate 가 DDL 생성 시 반영).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id",
            foreignKey = @ForeignKey(name = "fk_notification_event"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private HazardEvent event;

    // sos_request.sos_id. 테이블 생기기 전까지 FK 없음.
    @Column(name = "sos_id")
    private Long sosId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 10)
    private NotificationRecipientType recipientType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_worker_id",
            foreignKey = @ForeignKey(name = "fk_notification_recipient_worker"))
    private Worker recipientWorker;

    // manager_account.admin_id. 테이블 생기기 전까지 FK 없음.
    @Column(name = "recipient_admin_id")
    private Long recipientAdminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private NotificationChannel channel = NotificationChannel.PUSH;

    @Column(length = 255)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.SENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 10)
    @Builder.Default
    private NotificationTriggeredBy triggeredBy = NotificationTriggeredBy.SYSTEM;

    // manager_account.admin_id. 관리자가 수동으로 발생시킨 알림일 때만 채워진다.
    @Column(name = "triggered_by_admin_id")
    private Long triggeredByAdminId;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @PrePersist
    public void prePersist() {
        if (this.channel == null) {
            this.channel = NotificationChannel.PUSH;
        }
        if (this.status == null) {
            this.status = NotificationStatus.SENT;
        }
        if (this.triggeredBy == null) {
            this.triggeredBy = NotificationTriggeredBy.SYSTEM;
        }
        if (this.sentAt == null) {
            this.sentAt = LocalDateTime.now();
        }
    }

    /** 읽음 처리. 이미 읽은 알림이면 호출하지 않는 것이 호출자의 책임이다(현재는 멱등하게 허용). */
    public void confirm() {
        this.status = NotificationStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public boolean isRead() {
        return this.status == NotificationStatus.CONFIRMED;
    }
}
