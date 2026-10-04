package com.moode.server.dto.response;

import com.moode.server.domain.WorkerOverallStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/** 작업자 관리 목록(카드)의 한 항목. */
@Getter
@Builder
public class WorkerListItemResponse {

    private Long id;
    private String employeeNo;
    private String name;

    @Schema(nullable = true)
    private String zoneName;

    @Schema(nullable = true)
    private String floor;

    private WorkerOverallStatus status;

    @Schema(description = "최신 심박수. 센서 데이터가 아직 없으면 null", nullable = true)
    private Double heartRate;

    @Schema(description = "개인 기준선 대비 피로 이탈 여부. 센서 데이터가 아직 없으면 null", nullable = true)
    private Boolean fatigueAbnormal;

    @Schema(description = "카드 하단 상태 메시지 (예: \"추락 감지됨\", \"정상 작업 중\")")
    private String statusMessage;
}
