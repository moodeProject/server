package com.moode.server.dto.response;

import com.moode.server.domain.*;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

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

    // 건강 이상 사고의 발생 당시 생체 값. 생체 값을 저장하지 않는 영상·추락 사고면 null
    private HazardVitalsResponse vitals;

    // 마지막으로 상태를 바꾼 관리자와 시각. 미처리면 null
    private Long handledBy;
    private OffsetDateTime handledAt;

    // 조치 이력(시간순). 조치 전이면 빈 목록
    private List<HazardActionHistoryResponse> actions;
}
