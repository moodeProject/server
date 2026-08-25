package com.moode.server.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 서버(FastAPI) -> Spring 수신 메시지. HAZARD_CONFIRMED / CLIP_READY 가 같은 경로로 들어오고
 * eventType 으로 갈린다. 타입별로만 쓰이는 필드는 아래 주석 참고.
 *
 * <p>공통 필드만 @NotBlank 로 검증하고, 타입별 필수값은 서비스에서 검사해
 * IllegalArgumentException(-> 400)으로 알린다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
public class HazardEventIngestRequest {

    // 메시지 단위 식별자. 중복 판정은 오직 이 값으로만 한다.
    @NotBlank(message = "idempotencyKey 는 필수입니다.")
    private String idempotencyKey;

    // 사건 단위 식별자 -> hazard_event.event_uid. 여러 메시지가 같은 값을 가질 수 있다.
    @NotBlank(message = "eventId 는 필수입니다.")
    private String eventId;

    @NotBlank(message = "eventType 은 필수입니다.")
    private String eventType;

    // "H-001" 같은 헬멧 코드. hazard_event.helmet_id(BIGINT)로는 배정 조회를 통해 변환된다.
    @NotBlank(message = "helmetId 는 필수입니다.")
    private String helmetId;

    // ===== HAZARD_CONFIRMED 전용 =====
    private List<String> hazardTypes;
    private List<HazardDetectionRequest> detections;
    private Integer frameWidth;
    private Integer frameHeight;
    private OffsetDateTime occurredAt;

    // 클립 안에서의 사고 시점 마커. HAZARD_CONFIRMED 에 실려 오지만
    // CLIP_READY 에 실려 오면 그 값으로 hazard_clip.marked_offset_seconds 를 채운다.
    private BigDecimal streamOffsetSeconds;

    // ===== CLIP_READY 전용 =====
    private HazardClipRequest clip;
}
