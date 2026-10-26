package com.moode.server.service.impl;

import com.moode.server.domain.HazardEvent;
import com.moode.server.domain.Notification;
import com.moode.server.domain.NotificationRecipientType;
import com.moode.server.domain.NotificationStatus;
import com.moode.server.domain.NotificationTriggeredBy;
import com.moode.server.domain.Worker;
import com.moode.server.domain.Zone;
import com.moode.server.dto.response.NotificationListResponse;
import com.moode.server.dto.response.NotificationResponse;
import com.moode.server.exception.NotFoundException;
import com.moode.server.repository.NotificationRepository;
import com.moode.server.service.HazardNotificationService;
import com.moode.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 저장/조회 구현체.
 *
 * <p>{@link HazardNotificationService} 를 구현해 기존 {@code LoggingHazardNotificationService}
 * (로그만 남기는 임시 구현)를 대체한다 — 위험 이벤트 발생(HazardEventIngestServiceImpl,
 * SensorDataServiceImpl)과 관리자 조치(HazardEventActionServiceImpl.sendWarning/blockZone)가
 * 이미 이 인터페이스로 알림을 트리거하고 있어서, 구현체만 바꾸면 기존 호출부는 그대로 둔 채
 * 실제 저장이 시작된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService, HazardNotificationService {

    private final NotificationRepository notificationRepository;

    // ===================== 조회 / 읽음 (NotificationService) =====================

    @Override
    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(
            NotificationRecipientType recipientType, Long recipientId, Boolean isRead, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> result = findPage(recipientType, recipientId, isRead, pageable);
        long unreadCount = countUnread(recipientType, recipientId);

        return NotificationListResponse.builder()
                .content(result.getContent().stream().map(NotificationResponse::from).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .unreadCount(unreadCount)
                .build();
    }

    private Page<Notification> findPage(
            NotificationRecipientType type, Long recipientId, Boolean isRead, Pageable pageable) {

        if (type == NotificationRecipientType.WORKER) {
            if (recipientId == null) {
                throw new IllegalArgumentException("WORKER 조회는 recipientId(workerId)가 필요합니다.");
            }
            if (isRead == null) {
                return notificationRepository
                        .findByRecipientTypeAndRecipientWorkerIdOrderBySentAtDesc(type, recipientId, pageable);
            }
            return isRead
                    ? notificationRepository.findByRecipientTypeAndRecipientWorkerIdAndStatusOrderBySentAtDesc(
                            type, recipientId, NotificationStatus.CONFIRMED, pageable)
                    : notificationRepository.findByRecipientTypeAndRecipientWorkerIdAndStatusNotOrderBySentAtDesc(
                            type, recipientId, NotificationStatus.CONFIRMED, pageable);
        }

        // MANAGER. recipientId(adminId) 가 없으면 manager_account 가 아직 없는 동안의
        // "관리자 전체 공용 수신함"으로 취급한다.
        if (recipientId == null) {
            if (isRead == null) {
                return notificationRepository.findByRecipientTypeOrderBySentAtDesc(type, pageable);
            }
            return isRead
                    ? notificationRepository.findByRecipientTypeAndStatusOrderBySentAtDesc(
                            type, NotificationStatus.CONFIRMED, pageable)
                    : notificationRepository.findByRecipientTypeAndStatusNotOrderBySentAtDesc(
                            type, NotificationStatus.CONFIRMED, pageable);
        }
        if (isRead == null) {
            return notificationRepository
                    .findByRecipientTypeAndRecipientAdminIdOrderBySentAtDesc(type, recipientId, pageable);
        }
        return isRead
                ? notificationRepository.findByRecipientTypeAndRecipientAdminIdAndStatusOrderBySentAtDesc(
                        type, recipientId, NotificationStatus.CONFIRMED, pageable)
                : notificationRepository.findByRecipientTypeAndRecipientAdminIdAndStatusNotOrderBySentAtDesc(
                        type, recipientId, NotificationStatus.CONFIRMED, pageable);
    }

    private long countUnread(NotificationRecipientType type, Long recipientId) {
        if (type == NotificationRecipientType.WORKER) {
            return notificationRepository.countByRecipientTypeAndRecipientWorkerIdAndStatusNot(
                    type, recipientId, NotificationStatus.CONFIRMED);
        }
        if (recipientId == null) {
            return notificationRepository.countByRecipientTypeAndStatusNot(type, NotificationStatus.CONFIRMED);
        }
        return notificationRepository.countByRecipientTypeAndRecipientAdminIdAndStatusNot(
                type, recipientId, NotificationStatus.CONFIRMED);
    }

    @Override
    @Transactional
    public void markRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 알림입니다. " + notificationId));
        notification.confirm();
    }

    @Override
    @Transactional
    public int markAllRead(NotificationRecipientType recipientType, Long recipientId) {
        List<Notification> targets;
        if (recipientType == NotificationRecipientType.WORKER) {
            targets = notificationRepository.findByRecipientTypeAndRecipientWorkerIdAndStatusNot(
                    recipientType, recipientId, NotificationStatus.CONFIRMED);
        } else if (recipientId == null) {
            targets = notificationRepository.findByRecipientTypeAndStatusNot(
                    recipientType, NotificationStatus.CONFIRMED);
        } else {
            targets = notificationRepository.findByRecipientTypeAndRecipientAdminIdAndStatusNot(
                    recipientType, recipientId, NotificationStatus.CONFIRMED);
        }
        targets.forEach(Notification::confirm);
        return targets.size();
    }

    // ===================== SOS (NotificationService, 이지원님 SOS 모듈이 호출) =====================

    // REQUIRES_NEW: 호출한 SOS 저장 트랜잭션과 분리한다. 알림 저장이 실패해도 SOS 접수는 롤백되면 안 된다.
    // sos_id 에는 FK 가 없어서 SOS 가 아직 커밋되기 전이어도 저장에 문제가 없다.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifySosRaised(Long sosId, Long workerId, String workerName, String zoneName) {
        // recipientAdminId 를 비워 관리자 전체 공용 수신함으로 보낸다 (manager_account 생기기 전).
        notificationRepository.save(Notification.builder()
                .sosId(sosId)
                .recipientType(NotificationRecipientType.MANAGER)
                .message((workerName == null ? "작업자" : workerName)
                        + " · " + (zoneName == null ? "위치 미확인" : zoneName)
                        + " · SOS 요청")
                .triggeredBy(NotificationTriggeredBy.SYSTEM)
                .build());
        log.warn("[알림] SOS 접수 -> sosId={}, workerId={}", sosId, workerId);
    }

    // ===================== 건강 이상 (NotificationService, SensorDataServiceImpl 이 호출) =====================

    // 트랜잭션을 분리하지 않는다: 작업자 알림이 worker 행을 FK 로 참조하는데, 같은 센서 수신 요청에서
    // 작업자 위치를 갱신하므로 분리하면 서로 잠금을 기다릴 수 있다.
    @Override
    @Transactional
    public void notifyHealthAlert(Worker worker) {
        String zoneName = worker.getCurrentZone() == null
                ? "위치 미확인" : worker.getCurrentZone().getZoneName();

        // 작업자 본인
        notificationRepository.save(Notification.builder()
                .recipientType(NotificationRecipientType.WORKER)
                .recipientWorker(worker)
                .message("건강 이상 징후가 감지되었습니다. 작업을 멈추고 상태를 확인하세요.")
                .triggeredBy(NotificationTriggeredBy.SYSTEM)
                .build());

        // 관리자 공용 수신함 (manager_account 생기기 전이라 recipientAdminId 는 비워 둔다)
        notificationRepository.save(Notification.builder()
                .recipientType(NotificationRecipientType.MANAGER)
                .message(worker.getName() + " · " + zoneName + " · 건강 이상 징후(조치 필요)")
                .triggeredBy(NotificationTriggeredBy.SYSTEM)
                .build());

        log.warn("[알림] 건강 이상 -> {}({}), 구역={}", worker.getName(), worker.getEmployeeNo(), zoneName);
    }

    // ===================== 위험 이벤트 (HazardNotificationService) =====================

    @Override
    @Transactional
    public int notifyWorker(HazardEvent event, Worker worker) {
        if (worker == null) {
            log.warn("[알림] 대상 작업자가 없어 발송하지 못했습니다: eventId={}", event.getId());
            return 0;
        }
        notificationRepository.save(Notification.builder()
                .event(event)
                .recipientType(NotificationRecipientType.WORKER)
                .recipientWorker(worker)
                .message(event.resolveDescription())
                .triggeredBy(NotificationTriggeredBy.SYSTEM)
                .build());
        log.info("[알림] 위험 경고 저장 -> {}({}) : hazardType={}, severity={}, eventId={}",
                worker.getName(), worker.getEmployeeNo(),
                event.getHazardType(), event.getSeverity(), event.getId());
        return 1;
    }

    @Override
    @Transactional
    public int notifyZoneWorkers(HazardEvent event, Zone zone, List<Worker> workers) {
        workers.forEach(worker -> notificationRepository.save(Notification.builder()
                .event(event)
                .recipientType(NotificationRecipientType.WORKER)
                .recipientWorker(worker)
                .message(zone.getZoneName() + " 구역이 접근 금지로 설정되었습니다.")
                .triggeredBy(NotificationTriggeredBy.MANAGER)
                .build()));
        log.info("[알림] 구역 접근 금지 저장 -> {}({}) : 대상 {}명, eventId={}",
                zone.getZoneName(), zone.getZoneCode(), workers.size(), event.getId());
        return workers.size();
    }
}
