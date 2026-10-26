package com.moode.server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moode.server.common.Times;
import com.moode.server.config.HazardSeverityPolicy;
import com.moode.server.domain.*;
import com.moode.server.dto.request.HazardClipRequest;
import com.moode.server.dto.request.HazardDetectionRequest;
import com.moode.server.dto.request.HazardEventIngestRequest;
import com.moode.server.exception.NotFoundException;
import com.moode.server.repository.HazardClipRepository;
import com.moode.server.repository.HazardEventRepository;
import com.moode.server.repository.InboundMessageRepository;
import com.moode.server.service.HelmetAssignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 수신 처리의 트랜잭션 경계. 멱등 로그 기록과 실제 저장이 한 트랜잭션에서 함께 커밋/롤백되어야
 * "처리한 걸로 기록됐는데 저장은 안 된" 상태가 생기지 않는다.
 *
 * <p>{@link HazardEventIngestServiceImpl} 과 별도 빈으로 둔 이유: 중복 판정을 위해
 * DataIntegrityViolationException 을 잡아야 하는데, 같은 빈 안에서 잡으면 self-invocation 때문에
 * 프록시를 타지 않아 트랜잭션 경계가 생기지 않고, 예외를 잡아도 트랜잭션이
 * rollback-only 로 오염된 상태라 그대로 이어갈 수 없기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HazardEventWriter {

    private final HazardEventRepository hazardEventRepository;
    private final HazardClipRepository hazardClipRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final HazardSeverityPolicy hazardSeverityPolicy;
    private final ObjectMapper objectMapper;

    /**
     * HAZARD_CONFIRMED 저장. 알림은 커밋 이후에 보내야 하므로 여기서 보내지 않는다.
     */
    @Transactional
    public HazardEvent recordConfirmed(HazardEventIngestRequest request, HelmetAssignment assignment) {
        recordInbound(request, InboundEventType.HAZARD_CONFIRMED);

        HazardEvent saved = hazardEventRepository.save(
                buildExternalEvent(request, assignment, HazardSeverity.DANGER, ESCALATED_DESCRIPTION));
        log.info("위험 이벤트 저장(영상+센서 이상): id={}, eventUid={}, hazardType={}",
                saved.getId(), saved.getEventUid(), saved.getHazardType());
        return saved;
    }

    /** 영상과 헬멧 센서 이상이 겹쳐 위험으로 올린 이벤트의 설명. 영상 단독 감지는 이벤트를 만들지 않는다. */
    static final String ESCALATED_DESCRIPTION = "영상 위험 감지와 헬멧 센서 이상(자세·건강·추락)이 함께 발생.";

    /**
     * 영상 단독 감지. 대시보드 이벤트는 만들지 않고(헬멧 경고만), 같은 메시지가 재전송돼도
     * 경고가 두 번 나지 않도록 멱등 로그만 남긴다.
     *
     * @return 대표 위험 종류
     */
    @Transactional
    public HazardType recordWarningOnly(HazardEventIngestRequest request) {
        recordInbound(request, InboundEventType.HAZARD_CONFIRMED);
        requireOccurredAt(request);
        return pickMostSevere(parseHazardTypes(request.getHazardTypes()));
    }

    /** 아직 위험으로 오르지 않은 경고의 클립. 형식만 검증하고 멱등 로그를 남긴다. 보관은 호출자가 한다. */
    @Transactional
    public void recordClipForWarning(HazardEventIngestRequest request) {
        recordInbound(request, InboundEventType.CLIP_READY);
        if (request.getClip() == null) {
            throw new IllegalArgumentException("CLIP_READY 에는 clip 이 필요합니다.");
        }
        validateClip(request.getClip());
        parseStorageType(request.getClip().getStorage());
    }

    /**
     * 헬멧 센서 이상이 들어와 대기 중이던 영상 경고를 위험으로 올린다. 센서 수신 트랜잭션에 합류한다.
     * 경고 단계에서 맡아 둔 클립이 있으면 함께 붙인다.
     *
     * @return 저장된 이벤트와, 클립을 붙였는지
     */
    @Transactional
    public EscalationResult saveEscalated(VisionWarningRegistry.VisionWarning warning, HelmetAssignment assignment) {
        HazardEvent saved = hazardEventRepository.save(
                buildExternalEvent(warning.getRequest(), assignment, HazardSeverity.DANGER, ESCALATED_DESCRIPTION));

        HazardClipRequest clip = warning.getClip();
        if (clip != null) {
            hazardClipRepository.save(HazardClip.builder()
                    .hazardEvent(saved)
                    .storageType(parseStorageType(clip.getStorage()))
                    .bucket(clip.getBucket())
                    .objectKey(clip.getKey())
                    .durationSeconds(clip.getDurationSeconds())
                    .frameCount(clip.getFrameCount())
                    .markedOffsetSeconds(warning.getClipOffsetSeconds())
                    .build());
        }
        log.info("영상 경고를 위험으로 올림(센서 이상 동반): id={}, eventUid={}, hazardType={}, 클립={}",
                saved.getId(), saved.getEventUid(), saved.getHazardType(), clip != null);
        return new EscalationResult(saved, clip != null);
    }

    public record EscalationResult(HazardEvent event, boolean clipAttached) {
    }

    private HazardEvent buildExternalEvent(HazardEventIngestRequest request, HelmetAssignment assignment,
                                           HazardSeverity severity, String description) {
        requireOccurredAt(request);
        HazardType representative = pickMostSevere(parseHazardTypes(request.getHazardTypes()));

        return HazardEvent.builder()
                .eventUid(request.getEventId())
                .helmet(assignment.helmet())
                .worker(assignment.worker())
                .zone(assignment.zone())
                .category(HazardCategory.EXTERNAL)
                .hazardType(representative)
                .severity(severity)
                .confidence(pickConfidence(request.getDetections(), representative))
                .frameWidth(request.getFrameWidth())
                .frameHeight(request.getFrameHeight())
                .detectionsJson(serializeDetections(request))
                .occurredAt(toLocalDateTime(request.getOccurredAt()))
                .status(HazardStatus.UNHANDLED)
                .description(description)
                .build();
    }

    private void requireOccurredAt(HazardEventIngestRequest request) {
        if (request.getOccurredAt() == null) {
            throw new IllegalArgumentException("HAZARD_CONFIRMED 에는 occurredAt 이 필요합니다.");
        }
    }

    /**
     * CLIP_READY 처리. 같은 eventUid 를 가진 이벤트가 여러 건일 수 있고(한 사건에서 연달아 감지),
     * 클립은 그 사건 전체의 증거이므로 아직 클립이 없는 이벤트들에 각각 붙인다.
     *
     * @return 클립을 새로 붙인 이벤트 id 목록. 전부 이미 붙어 있었으면 빈 목록
     */
    @Transactional
    public List<Long> attachClip(HazardEventIngestRequest request) {
        recordInbound(request, InboundEventType.CLIP_READY);

        HazardClipRequest clipRequest = request.getClip();
        if (clipRequest == null) {
            throw new IllegalArgumentException("CLIP_READY 에는 clip 이 필요합니다.");
        }
        validateClip(clipRequest);

        List<HazardEvent> events = hazardEventRepository.findByEventUidOrderByOccurredAtAsc(request.getEventId());
        if (events.isEmpty()) {
            // CLIP_READY 가 HAZARD_CONFIRMED 보다 먼저 도착한 경우도 여기로 온다.
            // 404 는 재시도 없이 dead-letter 로 가는 코드라, 순서가 뒤집혀 도착하는 일이 실제로 관측되면
            // 404 대신 5xx 로 바꿔 재시도로 흡수하는 편이 낫다.
            throw new NotFoundException("해당 eventId 의 위험 이벤트가 없습니다. " + request.getEventId());
        }

        Set<Long> alreadyAttached = hazardClipRepository
                .findByHazardEventIdIn(events.stream().map(HazardEvent::getId).toList())
                .stream()
                .map(clip -> clip.getHazardEvent().getId())
                .collect(Collectors.toSet());

        StorageType storageType = parseStorageType(clipRequest.getStorage());
        List<Long> attached = new ArrayList<>();

        for (HazardEvent event : events) {
            if (alreadyAttached.contains(event.getId())) {
                continue;
            }
            hazardClipRepository.save(HazardClip.builder()
                    .hazardEvent(event)
                    .storageType(storageType)
                    .bucket(clipRequest.getBucket())
                    .objectKey(clipRequest.getKey())
                    .durationSeconds(clipRequest.getDurationSeconds())
                    .frameCount(clipRequest.getFrameCount())
                    .markedOffsetSeconds(request.getStreamOffsetSeconds())
                    .build());
            attached.add(event.getId());
        }

        log.info("클립 연결: eventUid={}, 대상 이벤트={}건, 신규 연결={}건",
                request.getEventId(), events.size(), attached.size());
        return attached;
    }

    // ===================== 멱등 로그 =====================

    /**
     * 멱등 로그를 먼저 남긴다. 이미 있는 키면 flush 시점에 PK 제약 위반이 나고,
     * 그 예외는 호출자가 중복으로 해석한다.
     */
    private void recordInbound(HazardEventIngestRequest request, InboundEventType eventType) {
        inboundMessageRepository.saveAndFlush(InboundMessage.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .eventUid(request.getEventId())
                .eventType(eventType.name())
                .build());
    }

    // ===================== 변환 / 판정 =====================

    private List<HazardType> parseHazardTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("HAZARD_CONFIRMED 에는 hazardTypes 가 필요합니다.");
        }
        return raw.stream().map(HazardType::from).toList();
    }

    /**
     * hazard_event 는 한 행에 hazardType 하나만 담는다(event_uid 당 여러 행이 가능하도록 설계됨).
     * 한 메시지에 여러 종류가 실려 오면 가장 높은 등급을 대표값으로 쓰고,
     * 나머지는 detections_json 에 원본 그대로 남겨 정보 손실을 막는다.
     */
    private HazardType pickMostSevere(List<HazardType> hazardTypes) {
        return hazardTypes.stream()
                .max(Comparator.comparingInt(type -> hazardSeverityPolicy.resolve(type).getLevel()))
                .orElseThrow(() -> new IllegalArgumentException("hazardTypes 가 비어 있습니다."));
    }

    private BigDecimal pickConfidence(List<HazardDetectionRequest> detections, HazardType representative) {
        if (detections == null || detections.isEmpty()) {
            return null;
        }
        return detections.stream()
                .filter(detection -> detection.getType() != null && detection.getConfidence() != null)
                .filter(detection -> representative.name().equalsIgnoreCase(detection.getType().trim()))
                .map(HazardDetectionRequest::getConfidence)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * bbox 원본을 그대로 보존한다. 직렬화에 실패해도 이벤트 저장과 알림은 살려야 하므로
     * 예외를 던지지 않고 null 로 둔다. 오버레이는 부가 정보고, 알림이 본질이다.
     */
    private String serializeDetections(HazardEventIngestRequest request) {
        if (request.getDetections() == null || request.getDetections().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(request.getDetections());
        } catch (JsonProcessingException e) {
            log.error("detections 직렬화 실패라 오버레이 데이터 없이 저장합니다: eventUid={}, {}",
                    request.getEventId(), e.getMessage());
            return null;
        }
    }

    /**
     * AI 서버는 오프셋이 붙은 시각(+00:00)을 보낸다. 저장은 LocalDateTime 이라
     * 서비스 기준 시간대로 환산해 넣는다. 변환 없이 잘라내면 9시간 어긋난다.
     */
    private LocalDateTime toLocalDateTime(java.time.OffsetDateTime occurredAt) {
        return Times.toLocal(occurredAt);
    }

    private StorageType parseStorageType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("clip.storage 는 필수입니다.");
        }
        try {
            return StorageType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 clip.storage 입니다. " + raw);
        }
    }

    private void validateClip(HazardClipRequest clip) {
        if (clip.getKey() == null || clip.getKey().isBlank()) {
            throw new IllegalArgumentException("clip.key 는 필수입니다.");
        }
        if (clip.getDurationSeconds() == null) {
            throw new IllegalArgumentException("clip.durationSeconds 는 필수입니다.");
        }
        if (clip.getFrameCount() == null) {
            throw new IllegalArgumentException("clip.frameCount 는 필수입니다.");
        }
    }
}
