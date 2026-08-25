package com.moode.server.dto.response;

import com.moode.server.common.Times;
import com.moode.server.domain.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 목록 항목. SSE 의 event: hazard 페이로드도 정확히 이 구조다.
 * 프론트가 실시간 수신분을 목록에 그대로 이어붙이기 때문에 둘이 달라지면 안 된다.
 */
@Getter
@Builder
public class HazardEventListItemResponse {

    private Long eventId;

    private HazardCategory category;

    // 응답에서는 소문자(puddle / pothole / forklift)로 나간다
    private HazardType hazardType;
    private String hazardLabel;

    private HazardSeverity severity;
    private String severityLabel;

    private String description;

    private WorkerSummaryResponse worker;
    private ZoneSummaryResponse zone;
    private String helmetNo;

    private BigDecimal confidence;
    private OffsetDateTime occurredAt;

    private HazardStatus status;
    private String statusLabel;

    // false 면 아직 녹화 중. 오류가 아니라 정상 상태다.
    private boolean hasClip;

    public static HazardEventListItemResponse of(HazardEvent event, boolean hasClip) {
        HazardType hazardType = event.getHazardType();
        return HazardEventListItemResponse.builder()
                .eventId(event.getId())
                .category(event.getCategory())
                .hazardType(hazardType)
                .hazardLabel(hazardType.getLabel())
                .severity(event.getSeverity())
                .severityLabel(event.getSeverity().getLabel())
                .description(event.resolveDescription())
                .worker(WorkerSummaryResponse.from(event.getWorker()))
                .zone(ZoneSummaryResponse.from(event.getZone()))
                .helmetNo(event.getHelmet() == null ? null : event.getHelmet().getHelmetNo())
                .confidence(event.getConfidence())
                .occurredAt(Times.toOffset(event.getOccurredAt()))
                .status(event.getStatus())
                .statusLabel(event.getStatus().getLabel())
                .hasClip(hasClip)
                .build();
    }
}
