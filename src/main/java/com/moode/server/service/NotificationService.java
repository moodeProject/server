package com.moode.server.service;

import com.moode.server.domain.NotificationRecipientType;
import com.moode.server.domain.Worker;
import com.moode.server.dto.response.NotificationListResponse;

/**
 * 알림 조회/읽음 API 전용 서비스.
 *
 * <p>알림 "생성"은 이 인터페이스가 아니라 기존 {@link HazardNotificationService} (위험 이벤트
 * 경로)와, SOS 가 생기면 그쪽에서 호출할 {@link #notifySosRaised} 를 통해서 이뤄진다.
 * 조회/읽음과 생성을 분리해 두면 SOS 모듈이 이 서비스 구현체(DB 저장)에만 의존하고
 * 위험 이벤트 쪽 타입을 몰라도 되게 된다.
 */
public interface NotificationService {

    /**
     * @param recipientType   WORKER / MANAGER (COWORKER 는 1차 범위 밖)
     * @param recipientId     WORKER 면 workerId, MANAGER 면 adminId. null 이면 해당 수신자
     *                        유형 전체(= manager_account 가 아직 없어 관리자 구분이 안 되는
     *                        동안의 공용 관리자 수신함)를 반환한다.
     * @param isRead          true/false 로 필터. null 이면 전체.
     */
    NotificationListResponse getNotifications(
            NotificationRecipientType recipientType, Long recipientId, Boolean isRead, int page, int size);

    void markRead(Long notificationId);

    /** @return 실제로 읽음 처리된 건수 */
    int markAllRead(NotificationRecipientType recipientType, Long recipientId);

    /**
     * SOS 발생 시 관리자에게 알릴 진입점. SOS 모듈(sos_request 테이블)이 아직 없어
     * SosRequest 엔티티에 의존하지 않도록 원시값만 받는다. 이지원님 SOS 작업에서 호출.
     */
    void notifySosRaised(Long sosId, Long workerId, String workerName, String zoneName);

    /**
     * 건강 이상(조치 필요 단계 진입) 알림. 작업자 본인과 관리자 공용 수신함에 1건씩 저장한다.
     * 건강 이상은 hazard_event 를 만들지 않으므로 event 가 비어 있는 알림이다.
     * 센서 수신(SensorDataServiceImpl)에서 "상태가 올라간 순간"에만 호출한다.
     */
    void notifyHealthAlert(Worker worker);
}
