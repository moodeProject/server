package com.moode.server.dto.response;

import com.moode.server.domain.WorkerOverallStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/** 작업자 관리 상세 — 외부요인/건강/추락 3블록. */
@Getter
@Builder
public class WorkerDetailResponse {

    private Long id;
    private String employeeNo;
    private String name;
    private WorkerOverallStatus status;

    @Schema(nullable = true)
    private String zoneName;

    @Schema(nullable = true)
    private String floor;

    @Schema(description = "배정된 헬멧 번호. 미배정이면 null", nullable = true)
    private String helmetNo;

    @Schema(description = "헬멧이 배정돼 있고 현재 센서 연결 상태이면 true")
    private boolean sensorConnected;

    private ExternalBlock external;
    private HealthBlock health;
    private FallBlock fall;

    @Getter
    @Builder
    public static class ExternalBlock {
        private WorkerOverallStatus status;
        @Schema(description = "예: \"위험 요소 없음\", \"물웅덩이 감지\"")
        private String message;
    }

    @Getter
    @Builder
    public static class HealthBlock {
        private WorkerOverallStatus status;
        @Schema(nullable = true)
        private Double heartRate;
        @Schema(nullable = true)
        private Boolean fatigueAbnormal;
    }

    @Getter
    @Builder
    public static class FallBlock {
        private WorkerOverallStatus status;
        @Schema(description = "예: \"감지 없음\", \"추락 감지됨\"")
        private String message;
    }
}
