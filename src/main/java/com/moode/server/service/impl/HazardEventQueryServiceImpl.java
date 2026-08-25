package com.moode.server.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moode.server.common.Times;
import com.moode.server.domain.*;
import com.moode.server.dto.response.*;
import com.moode.server.exception.NotFoundException;
import com.moode.server.repository.HazardClipRepository;
import com.moode.server.repository.HazardEventRepository;
import com.moode.server.repository.HazardEventSpecifications;
import com.moode.server.service.ClipPlaybackUrl;
import com.moode.server.service.ClipUrlIssuer;
import com.moode.server.service.HazardEventQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HazardEventQueryServiceImpl implements HazardEventQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final HazardEventRepository hazardEventRepository;
    private final HazardClipRepository hazardClipRepository;
    private final ClipUrlIssuer clipUrlIssuer;
    private final ObjectMapper objectMapper;

    @Override
    public HazardEventListResponse search(HazardCategory category,
                                          HazardSeverity severity,
                                          HazardStatus status,
                                          Long workerId,
                                          OffsetDateTime from,
                                          OffsetDateTime to,
                                          int page,
                                          int size) {

        LocalDateTime fromLocal = toLocal(from);
        LocalDateTime toLocal = toLocal(to);
        if (fromLocal != null && toLocal != null && fromLocal.isAfter(toLocal)) {
            throw new IllegalArgumentException("from 이 to 보다 늦을 수 없습니다.");
        }

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                // 최신순. 같은 시각이면 id 로 안정 정렬한다.
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<HazardEvent> result = hazardEventRepository.findAll(
                HazardEventSpecifications.filter(category, severity, status, workerId, fromLocal, toLocal),
                pageRequest);

        // 클립 유무는 한 번에 조회한다. 항목마다 조회하면 페이지 크기만큼 쿼리가 나간다.
        Set<Long> withClip = findEventIdsWithClip(result.getContent());

        return HazardEventListResponse.builder()
                .content(result.getContent().stream()
                        .map(event -> HazardEventListItemResponse.of(event, withClip.contains(event.getId())))
                        .toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .summary(buildSummary())
                .build();
    }

    @Override
    public HazardEventDetailResponse getDetail(Long eventId) {
        HazardEvent event = hazardEventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 위험 이벤트입니다. " + eventId));

        HazardType hazardType = event.getHazardType();

        // 클립은 감지보다 10~20초 늦게 도착하므로 없는 게 정상 상태다.
        HazardClipResponse clip = hazardClipRepository.findByHazardEventId(eventId)
                .map(this::toClipResponse)
                .orElse(null);

        return HazardEventDetailResponse.builder()
                .eventId(event.getId())
                .category(event.getCategory())
                .hazardType(hazardType)
                .hazardLabel(hazardType.getLabel())
                .severity(event.getSeverity())
                .severityLabel(event.getSeverity().getLabel())
                .riskDescription(hazardType.getRiskDescription())
                .detail(event.resolveDescription())
                .recommendedAction(hazardType.getRecommendedAction())
                .worker(WorkerSummaryResponse.from(event.getWorker()))
                .zone(ZoneSummaryResponse.from(event.getZone()))
                .helmetNo(event.getHelmet() == null ? null : event.getHelmet().getHelmetNo())
                .occurredAt(Times.toOffset(event.getOccurredAt()))
                .status(event.getStatus())
                .statusLabel(event.getStatus().getLabel())
                .clip(clip)
                // detection 은 클립보다 먼저 도착하므로 영상이 없어도 항상 채운다.
                .detection(HazardDetectionResponse.builder()
                        .frameWidth(event.getFrameWidth())
                        .frameHeight(event.getFrameHeight())
                        .boxes(parseBoxes(event))
                        .build())
                .build();
    }

    // ===================== 보조 =====================

    private LocalDateTime toLocal(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Set<Long> findEventIdsWithClip(List<HazardEvent> events) {
        if (events.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = events.stream().map(HazardEvent::getId).toList();
        return hazardClipRepository.findByHazardEventIdIn(ids).stream()
                .map(clip -> clip.getHazardEvent().getId())
                .collect(Collectors.toSet());
    }

    /**
     * 필터 탭 배지용 집계. 명세상 현재 필터와 무관하게 항상 전체 기준이다.
     */
    private HazardEventSummaryCountResponse buildSummary() {
        Map<HazardCategory, Long> counts = new EnumMap<>(HazardCategory.class);
        for (Object[] row : hazardEventRepository.countByCategory()) {
            counts.put((HazardCategory) row[0], (Long) row[1]);
        }
        long external = counts.getOrDefault(HazardCategory.EXTERNAL, 0L);
        long health = counts.getOrDefault(HazardCategory.HEALTH, 0L);
        long fall = counts.getOrDefault(HazardCategory.FALL, 0L);

        return HazardEventSummaryCountResponse.builder()
                .all(external + health + fall)
                .external(external)
                .health(health)
                .fall(fall)
                .build();
    }

    private HazardClipResponse toClipResponse(HazardClip clip) {
        // 저장된 URL 을 주는 게 아니라 조회 시점에 발급한다. 그래야 만료를 통제할 수 있다.
        ClipPlaybackUrl playbackUrl = clipUrlIssuer.issue(clip);
        return HazardClipResponse.of(clip, playbackUrl);
    }

    /**
     * 저장해둔 원본 JSON 을 오버레이용 박스 배열로 되돌린다.
     * 파싱에 실패해도 상세 조회 전체를 실패시키지 않는다. 오버레이만 빠지고 나머지는 보여줘야 한다.
     */
    private List<HazardDetectionBoxResponse> parseBoxes(HazardEvent event) {
        String raw = event.getDetectionsJson();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<HazardDetectionBoxResponse>>() {
            });
        } catch (Exception e) {
            log.error("detections 파싱 실패라 오버레이 없이 응답합니다: eventId={}, {}", event.getId(), e.getMessage());
            return List.of();
        }
    }
}
