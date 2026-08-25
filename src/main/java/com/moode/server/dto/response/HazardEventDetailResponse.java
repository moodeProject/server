package com.moode.server.dto.response;

import com.moode.server.domain.*;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 상세 응답.
 *
 * <p>detection 은 클립보다 먼저 도착하므로 clip 이 null 이어도 항상 채워진다.
 * 프론트는 clip == null 이면 "영상 준비 중"으로 표시하고 감지 정보는 그대로 보여준다.
 */
@Getter
@Builder
public class HazardEventDetailResponse {

    private Long eventId;

    private HazardCategory category;
    private HazardType hazardType;
    private String hazardLabel;

    private HazardSeverity severity;
    private String severityLabel;

    private String riskDescription;
    private String detail;
    private String recommendedAction;

    private WorkerSummaryResponse worker;
    private ZoneSummaryResponse zone;
    private String helmetNo;

    private OffsetDateTime occurredAt;

    private HazardStatus status;
    private String statusLabel;

    // 아직 녹화 중이면 null
    private HazardClipResponse clip;

    private HazardDetectionResponse detection;
}
