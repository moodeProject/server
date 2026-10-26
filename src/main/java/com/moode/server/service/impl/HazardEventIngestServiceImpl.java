package com.moode.server.service.impl;

import com.moode.server.domain.HazardEvent;
import com.moode.server.domain.HazardType;
import com.moode.server.domain.InboundEventType;
import com.moode.server.dto.request.HazardEventIngestRequest;
import com.moode.server.repository.InboundMessageRepository;
import com.moode.server.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 수신 오케스트레이션. 트랜잭션은 {@link HazardEventWriter} 가 담당하고 여기서는 걸지 않는다.
 * 알림 발송이 커밋 이후에 일어나야 하고, 중복 판정 예외를 잡은 뒤에도 조회를 계속해야 하기 때문.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HazardEventIngestServiceImpl implements HazardEventIngestService {

    private final HazardEventWriter hazardEventWriter;
    private final InboundMessageRepository inboundMessageRepository;
    private final HelmetAssignmentResolver helmetAssignmentResolver;
    private final HazardNotificationService hazardNotificationService;
    private final HazardEventStreamService hazardEventStreamService;
    private final VisionWarningRegistry visionWarningRegistry;

    @Override
    public HazardIngestResult receive(HazardEventIngestRequest request) {
        InboundEventType eventType = InboundEventType.from(request.getEventType());

        // 중복 판정은 오직 idempotencyKey 로만 한다. eventId 로 판정하면 한 사건에서 연달아
        // 감지된 다른 위험(물웅덩이 -> 지게차)의 알림이 통째로 사라진다.
        if (inboundMessageRepository.existsById(request.getIdempotencyKey())) {
            log.info("이미 처리한 메시지라 무시: idempotencyKey={}, eventUid={}",
                    request.getIdempotencyKey(), request.getEventId());
            return HazardIngestResult.duplicateIgnored();
        }

        try {
            return switch (eventType) {
                case HAZARD_CONFIRMED -> handleHazardConfirmed(request);
                case CLIP_READY -> handleClipReady(request);
            };
        } catch (DataIntegrityViolationException e) {
            // 위 존재 확인과 저장 사이에 같은 메시지가 동시에 들어온 경우. 멱등 로그가 실제로
            // 생겼는지 다시 확인해서, 다른 제약 위반을 중복으로 오인하지 않도록 한다.
            if (inboundMessageRepository.existsById(request.getIdempotencyKey())) {
                log.info("동시 수신으로 중복 감지: idempotencyKey={}", request.getIdempotencyKey());
                return HazardIngestResult.duplicateIgnored();
            }
            throw e;
        }
    }

    /**
     * 영상 단독 감지는 헬멧 경고로만 처리하고, 헬멧 센서 이상(자세·건강·추락)이 앞뒤
     * {@link VisionWarningRegistry#ESCALATION_WINDOW} 안에 겹칠 때만 대시보드 위험 이벤트로 올린다.
     * 센서 이상이 나중에 오는 경우는 {@link SensorDataServiceImpl} 이 올린다.
     */
    private HazardIngestResult handleHazardConfirmed(HazardEventIngestRequest request) {
        HelmetAssignment assignment = helmetAssignmentResolver.resolve(request.getHelmetId());
        String deviceId = assignment.helmet().getDeviceId();

        if (!visionWarningRegistry.sensorAbnormalWithin(deviceId)) {
            HazardType hazardType = hazardEventWriter.recordWarningOnly(request);
            visionWarningRegistry.record(deviceId, request, hazardType, false);
            log.info("영상 단독 감지 — 헬멧 경고만: helmetId={}, deviceId={}, hazardType={}",
                    request.getHelmetId(), deviceId, hazardType);
            return HazardIngestResult.received(null);
        }

        HazardEvent saved = hazardEventWriter.recordConfirmed(request, assignment);
        visionWarningRegistry.record(deviceId, request, saved.getHazardType(), true);

        notifyWorker(saved, assignment);
        // 저장 직후 대시보드로 push. 이걸 빠뜨리면 실시간 목록이 갱신되지 않는다.
        publish(() -> hazardEventStreamService.publishHazard(saved), saved.getId());

        return HazardIngestResult.received(saved.getId());
    }

    private HazardIngestResult handleClipReady(HazardEventIngestRequest request) {
        // 아직 경고 단계라 붙일 이벤트가 없다. 404 를 주면 AI 서버가 dead-letter 로 보내므로
        // 경고에 맡겨 두었다가 위험으로 오를 때 붙인다.
        Optional<VisionWarningRegistry.VisionWarning> pending = visionWarningRegistry.findPending(request.getEventId());
        if (pending.isPresent()) {
            hazardEventWriter.recordClipForWarning(request);
            // 클립은 확정 후 10초 넘게 지나서 오므로, 보관하는 바로 그 순간 위험으로 오르는 경합은
            // 무시한다. 그 경우 이벤트는 남고 클립만 빠진다.
            pending.get().keepClip(request.getClip(), request.getStreamOffsetSeconds());
            log.info("경고 단계 클립 보관: eventUid={}", request.getEventId());
            return HazardIngestResult.received(null);
        }

        return attachToEvents(hazardEventWriter.attachClip(request), request);
    }

    private HazardIngestResult attachToEvents(List<Long> attached, HazardEventIngestRequest request) {
        if (attached.isEmpty()) {
            // 이벤트는 있는데 이미 클립이 붙어 있는 경우(다른 idempotencyKey 로 재전송된 CLIP_READY).
            // 새로 한 일이 없으므로 AI 서버에는 완료로 알린다.
            log.info("이미 클립이 연결되어 있어 무시: eventUid={}", request.getEventId());
            return HazardIngestResult.duplicateIgnored();
        }

        attached.forEach(eventId ->
                publish(() -> hazardEventStreamService.publishClipReady(eventId), eventId));

        return HazardIngestResult.received(attached.get(0));
    }

    /**
     * 알림 실패는 요청을 실패시키지 않는다. 여기서 예외를 던져 5xx 를 주면 AI 서버가 재시도하는데,
     * 재시도분은 멱등 로그에 걸려 DUPLICATE_IGNORED 로 끝나 알림이 다시 시도되지도 않는다.
     * 즉 재시도로 얻는 게 없고 저장된 이벤트만 롤백된다.
     */
    /**
     * SSE push 는 저장 트랜잭션 밖에서, 실패해도 삼킨다.
     * 구독자 하나가 끊겼다는 이유로 500 을 주면 AI 서버가 이미 저장된 이벤트를 재시도하게 되고,
     * 그 재시도는 멱등 로그에 걸려 DUPLICATE_IGNORED 로 끝나므로 얻는 것도 없다.
     */
    private void publish(Runnable push, Long eventId) {
        try {
            push.run();
        } catch (Exception e) {
            log.error("SSE push 실패(저장은 완료됨): eventId={}, {}", eventId, e.getMessage(), e);
        }
    }

    private void notifyWorker(HazardEvent event, HelmetAssignment assignment) {
        try {
            hazardNotificationService.notifyWorker(event, assignment.worker());
        } catch (Exception e) {
            log.error("알림 발송 실패(이벤트는 저장됨): eventId={}, eventUid={}, {}",
                    event.getId(), event.getEventUid(), e.getMessage(), e);
        }
    }
}
