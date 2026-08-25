package com.moode.server.service.impl;

import com.moode.server.domain.HazardEvent;
import com.moode.server.domain.Worker;
import com.moode.server.domain.Zone;
import com.moode.server.service.HazardNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FCM 연동 전까지 쓰는 로그 기반 임시 구현.
 *
 * <p>TODO: FCM 을 붙일 때 infrastructure 에 FcmHazardNotificationService 를 만들어 이 빈을 대체할 것.
 * 그때 ERD 의 notification 테이블에 발송 이력을 남기는 것도 함께 하면 된다.
 * 기존 SensorDataServiceImpl.triggerAlert() 의 TODO 도 같은 시점에 이 인터페이스로 흡수 가능.
 */
@Slf4j
@Service
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
