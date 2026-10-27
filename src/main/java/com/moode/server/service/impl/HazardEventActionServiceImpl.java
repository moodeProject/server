package com.moode.server.service.impl;

import com.moode.server.common.Times;
import com.moode.server.domain.*;
import com.moode.server.dto.request.HazardActionRequest;
import com.moode.server.dto.response.HazardActionResponse;
import com.moode.server.exception.ConflictException;
import com.moode.server.exception.NotFoundException;
import com.moode.server.exception.UnprocessableEntityException;
import com.moode.server.repository.HazardEventActionRepository;
import com.moode.server.repository.HazardEventRepository;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.service.HazardEventActionService;
import com.moode.server.service.HazardNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HazardEventActionServiceImpl implements HazardEventActionService {

    private final HazardEventRepository hazardEventRepository;
    private final HazardEventActionRepository hazardEventActionRepository;
    private final WorkerRepository workerRepository;
    private final HelmetRepository helmetRepository;
    private final HazardNotificationService hazardNotificationService;

    @Override
    @Transactional
    public HazardActionResponse act(Long eventId, HazardActionRequest request) {
        HazardActionType actionType = HazardActionType.from(request.getActionType());

        HazardEvent event = hazardEventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 위험 이벤트입니다. " + eventId));

        // 중복 클릭 방지. 프론트는 409 를 받으면 버튼을 비활성화한다.
        if (hazardEventActionRepository.existsByHazardEventIdAndActionType(eventId, actionType)) {
            throw new ConflictException("이미 실행한 조치입니다. " + actionType.name());
        }

        ActionOutcome outcome = switch (actionType) {
            case SEND_WARNING -> sendWarning(event);
            case BLOCK_ZONE -> blockZone(event, request.getAdminId());
            case RESOLVE -> resolve();
        };

        event.handle(actionType.getResultingStatus(), request.getAdminId());

        HazardEventAction action = hazardEventActionRepository.save(HazardEventAction.builder()
                .hazardEvent(event)
                .actionType(actionType)
                .performedBy(request.getAdminId())
                .notifiedCount(outcome.notifiedCount())
                .memo(request.getMemo())
                .build());

        log.info("조치 기록: eventId={}, actionType={}, adminId={}, notified={}, memo={}",
                eventId, actionType, request.getAdminId(), outcome.notifiedCount(), request.getMemo());

        return HazardActionResponse.builder()
                .eventId(event.getId())
                .actionType(actionType)
                .status(event.getStatus())
                .statusLabel(event.getStatus().getLabel())
                .performedAt(Times.toOffset(action.getPerformedAt()))
                .notifiedCount(outcome.notifiedCount())
                .message(outcome.message())
                .build();
    }

    private record ActionOutcome(int notifiedCount, String message) {
    }

    private ActionOutcome sendWarning(HazardEvent event) {
        Worker worker = event.getWorker();
        if (worker == null) {
            throw new UnprocessableEntityException("이벤트에 연결된 작업자가 없어 알림을 보낼 수 없습니다.");
        }
        // 명세의 422 사유: "작업자에게 연결된 기기가 없어 발송 불가"
        if (!helmetRepository.existsByWorkerId(worker.getId())) {
            throw new UnprocessableEntityException(
                    worker.getName() + " 작업자에게 연결된 기기가 없어 알림을 보낼 수 없습니다.");
        }

        int notified = hazardNotificationService.notifyWorker(event, worker);
        return new ActionOutcome(notified,
                worker.getName() + " 작업자에게 경고 알림을 전송했습니다.");
    }

    private ActionOutcome blockZone(HazardEvent event, Long adminId) {
        Zone zone = event.getZone();
        if (zone == null) {
            throw new UnprocessableEntityException("이벤트에 연결된 구역이 없어 접근 금지를 적용할 수 없습니다.");
        }
        if (zone.isAccessRestricted()) {
            throw new ConflictException("이미 접근 금지된 구역입니다. " + zone.getZoneCode());
        }

        zone.restrictAccess(adminId);

        List<Worker> workers = workerRepository.findByCurrentZoneId(zone.getId());
        int notified = hazardNotificationService.notifyZoneWorkers(event, zone, workers);

        return new ActionOutcome(notified,
                zone.getZoneName() + " 구역을 접근 금지로 설정하고 작업자 " + notified + "명에게 알렸습니다.");
    }

    private ActionOutcome resolve() {
        // 상태만 바꾸는 조치라 알림 대상이 없다.
        return new ActionOutcome(0, "확인 완료로 처리했습니다.");
    }
}
