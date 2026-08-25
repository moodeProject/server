package com.moode.server.controller;

import com.moode.server.dto.request.HazardEventIngestRequest;
import com.moode.server.dto.response.HazardIngestResponse;
import com.moode.server.service.HazardEventIngestService;
import com.moode.server.service.HazardIngestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 서버(FastAPI) 전용 내부 수신 엔드포인트. X-Internal-Api-Key 로 보호된다.
 *
 * <p>응답을 ApiResponse 로 감싸지 않는다. AI 서버와의 연동 규약이 본문 모양까지 고정하고 있어서다.
 * 프론트용 API(/api/**)는 기존대로 ApiResponse 를 쓴다.
 *
 * <p>상태코드가 곧 재시도 지시다.
 * 201/200 완료, 400/401/404 재시도 없이 dead-letter, 429/5xx 지수 백오프 재시도.
 */
@Tag(name = "위험 이벤트 수신(내부)", description = "AI 서버 -> 서버 위험 감지 이벤트 수신 API")
@RestController
@RequestMapping("/internal/v1/hazard-events")
@RequiredArgsConstructor
public class HazardEventIngestController {

    private final HazardEventIngestService hazardEventIngestService;

    @Operation(summary = "위험 감지 / 클립 완성 이벤트 수신")
    @PostMapping
    public ResponseEntity<HazardIngestResponse> receive(
            @Valid @RequestBody HazardEventIngestRequest request) {

        HazardIngestResult result = hazardEventIngestService.receive(request);

        return ResponseEntity
                .status(result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(HazardIngestResponse.builder()
                        .eventId(result.eventId())
                        .status(result.status().name())
                        .build());
    }
}
