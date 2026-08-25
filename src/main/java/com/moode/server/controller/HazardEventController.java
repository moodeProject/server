package com.moode.server.controller;

import com.moode.server.domain.HazardCategory;
import com.moode.server.domain.HazardSeverity;
import com.moode.server.domain.HazardStatus;
import com.moode.server.dto.request.HazardActionRequest;
import com.moode.server.dto.response.HazardActionResponse;
import com.moode.server.dto.response.HazardEventDetailResponse;
import com.moode.server.dto.response.HazardEventListResponse;
import com.moode.server.service.HazardEventActionService;
import com.moode.server.service.HazardEventQueryService;
import com.moode.server.service.HazardEventStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;

/**
 * 관리자 대시보드용 위험 이벤트 API.
 *
 * <p>프론트 명세가 본문 모양을 고정하고 있어 기존 /api/** 와 달리 ApiResponse 로 감싸지 않는다.
 * 오류 응답만 GlobalExceptionHandler 를 거쳐 ApiResponse 형태로 나가며,
 * 프론트는 상태코드로 분기하므로 문제되지 않는다.
 */
@Tag(name = "위험 이벤트", description = "Frontend: 이상 감지 목록 / 상세 / 조치 / 실시간 수신 API")
@RestController
@RequestMapping("/api/v1/hazard-events")
@RequiredArgsConstructor
public class HazardEventController {

    private final HazardEventQueryService hazardEventQueryService;
    private final HazardEventActionService hazardEventActionService;
    private final HazardEventStreamService hazardEventStreamService;

    @Operation(summary = "이상 감지 목록", description = "최신순. category/severity/status/workerId/기간 필터와 페이지네이션.")
    @GetMapping
    public HazardEventListResponse search(
            @RequestParam(required = false) HazardCategory category,
            @RequestParam(required = false) HazardSeverity severity,
            @RequestParam(required = false) HazardStatus status,
            @RequestParam(required = false) Long workerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return hazardEventQueryService.search(category, severity, status, workerId, from, to, page, size);
    }

    @Operation(summary = "실시간 수신(SSE)",
            description = "event: hazard(목록 항목과 동일, hasClip=false) / clip-ready({eventId}) / heartbeat(30초)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return hazardEventStreamService.subscribe();
    }

    @Operation(summary = "이상 감지 상세",
            description = "clip.playbackUrl 은 요청 시점에 발급되는 15분짜리 임시 URL 이다. 캐시하면 안 된다.")
    @GetMapping("/{eventId}")
    public HazardEventDetailResponse getDetail(@PathVariable Long eventId) {
        return hazardEventQueryService.getDetail(eventId);
    }

    @Operation(summary = "조치 실행", description = "SEND_WARNING / BLOCK_ZONE / RESOLVE")
    @PostMapping("/{eventId}/actions")
    public HazardActionResponse act(@PathVariable Long eventId,
                                    @Valid @RequestBody HazardActionRequest request) {
        return hazardEventActionService.act(eventId, request);
    }
}
