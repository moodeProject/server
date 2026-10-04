package com.moode.server.service.impl;

import com.moode.server.domain.HazardEvent;
import com.moode.server.domain.Worker;
import com.moode.server.domain.Zone;
import com.moode.server.service.HazardNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FCM 연동 전까지 쓰던 로그 기반 임시 구현.
 *
 * <p>(2026-10) {@link NotificationServiceImpl} 로 대체되어 더 이상 빈으로 등록하지 않는다
 * (@Service 제거). notification 테이블에 실제로 저장하는 구현으로 교체되었다 — 근태/알림
 * API 작업 중 변경. 참고용으로 클래스는 남겨 둔다. FCM 연동 시에는 NotificationServiceImpl
 * 내부에서 실제 발송 채널을 호출하도록 확장하면 된다.
 */
@Slf4j
public class LoggingHazardNotificationService implements HazardNotificationService {

    @Override
    public int notifyWorker(HazardEvent event, Worker worker) {
        if (worker == null) {
            log.warn("[알림] 대상 작업자가 없어 발송하지 못했습니다: eventId={}", event.getId());
            return 0;
        }
        log.warn("[알림] 위험 경고 -> {}({}) : hazardType={}, severity={}, eventId={}",
                worker.getName(), worker.getEmployeeNo(),
                event.getHazardType(), event.getSeverity(), event.getId());
        return 1;
    }

    @Override
    public int notifyZoneWorkers(HazardEvent event, Zone zone, List<Worker> workers) {
        log.warn("[알림] 구역 접근 금지 -> {}({}) : 대상 {}명, eventId={}",
                zone.getZoneName(), zone.getZoneCode(), workers.size(), event.getId());
        workers.forEach(worker ->
                log.info("[알림] 접근 금지 통보: {}({})", worker.getName(), worker.getEmployeeNo()));
        return workers.size();
    }
}
