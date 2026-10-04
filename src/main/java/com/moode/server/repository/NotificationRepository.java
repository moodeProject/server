package com.moode.server.repository;

import com.moode.server.domain.Notification;
import com.moode.server.domain.NotificationRecipientType;
import com.moode.server.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * recipientAdminId 는 manager_account 가 아직 없어 null 일 수 있다 — 그 경우 "관리자 전체
 * 공용 수신함"으로 취급해 recipientAdminId 조건 없이(= recipientId 가 지정되지 않은 경우)
 * 조회한다. isRead 필터는 status 로 변환해서 쓴다 (CONFIRMED = 읽음, 그 외 = 안읽음).
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientTypeOrderBySentAtDesc(
            NotificationRecipientType recipientType, Pageable pageable);

    Page<Notification> findByRecipientTypeAndStatusOrderBySentAtDesc(
            NotificationRecipientType recipientType, NotificationStatus status, Pageable pageable);

    Page<Notification> findByRecipientTypeAndStatusNotOrderBySentAtDesc(
            NotificationRecipientType recipientType, NotificationStatus status, Pageable pageable);

    Page<Notification> findByRecipientTypeAndRecipientWorkerIdOrderBySentAtDesc(
            NotificationRecipientType recipientType, Long recipientWorkerId, Pageable pageable);

    Page<Notification> findByRecipientTypeAndRecipientWorkerIdAndStatusOrderBySentAtDesc(
            NotificationRecipientType recipientType, Long recipientWorkerId,
            NotificationStatus status, Pageable pageable);

    Page<Notification> findByRecipientTypeAndRecipientWorkerIdAndStatusNotOrderBySentAtDesc(
            NotificationRecipientType recipientType, Long recipientWorkerId,
            NotificationStatus status, Pageable pageable);

    Page<Notification> findByRecipientTypeAndRecipientAdminIdOrderBySentAtDesc(
            NotificationRecipientType recipientType, Long recipientAdminId, Pageable pageable);

    Page<Notification> findByRecipientTypeAndRecipientAdminIdAndStatusOrderBySentAtDesc(
            NotificationRecipientType recipientType, Long recipientAdminId,
            NotificationStatus status, Pageable pageable);

    Page<Notification> findByRecipientTypeAndRecipientAdminIdAndStatusNotOrderBySentAtDesc(
            NotificationRecipientType recipientType, Long recipientAdminId,
            NotificationStatus status, Pageable pageable);

    long countByRecipientTypeAndStatusNot(NotificationRecipientType recipientType, NotificationStatus status);

    long countByRecipientTypeAndRecipientWorkerIdAndStatusNot(
            NotificationRecipientType recipientType, Long recipientWorkerId, NotificationStatus status);

    long countByRecipientTypeAndRecipientAdminIdAndStatusNot(
            NotificationRecipientType recipientType, Long recipientAdminId, NotificationStatus status);

    java.util.List<Notification> findByRecipientTypeAndStatusNot(
            NotificationRecipientType recipientType, NotificationStatus status);

    java.util.List<Notification> findByRecipientTypeAndRecipientWorkerIdAndStatusNot(
            NotificationRecipientType recipientType, Long recipientWorkerId, NotificationStatus status);

    java.util.List<Notification> findByRecipientTypeAndRecipientAdminIdAndStatusNot(
            NotificationRecipientType recipientType, Long recipientAdminId, NotificationStatus status);
}
