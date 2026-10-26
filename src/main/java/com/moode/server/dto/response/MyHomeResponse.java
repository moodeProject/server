package com.moode.server.dto.response;

import com.moode.server.domain.Helmet;
import com.moode.server.domain.SensorStatus;
import com.moode.server.domain.Worker;
import com.moode.server.domain.WorkerState;
import com.moode.server.domain.WorkerStatus;
import com.moode.server.domain.Zone;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 작업자 앱 홈 화면(docs/ui/worker) — 로그인한 작업자 본인의 상태 카드 + 건강 카드.
 * 관리자용 목록 API 를 걸러 쓰지 않도록 화면에 필요한 값만 한 번에 내려준다.
 */
@Getter
@Builder
public class MyHomeResponse {

    private WorkerBlock worker;

    @Schema(description = "배정된 헬멧. 미배정이면 null", nullable = true)
    private HelmetBlock helmet;

    @Schema(description = "가장 최근 센서 측정값. 헬멧 미배정이거나 아직 수신한 데이터가 없으면 null", nullable = true)
    private SensorBlock sensor;

    @Getter
    @Builder
    public static class WorkerBlock {
        private Long workerId;
        private String employeeNo;
        private String name;

        @Schema(description = "근무 상태. WORKING(근무 중) / BREAK(휴식) / OFF_DUTY(퇴근)")
        private WorkerStatus status;

        @Schema(description = "현재 위치한 구역. 아직 판정된 구역이 없으면 null", nullable = true)
        private ZoneBlock zone;
    }

    @Getter
    @Builder
    public static class ZoneBlock {
        private Long zoneId;
        private String zoneCode;
        private String zoneName;

        @Schema(nullable = true)
        private String floor;
    }

    @Getter
    @Builder
    public static class HelmetBlock {
        private Long helmetId;
        private String helmetNo;
        private String deviceId;

        @Schema(description = "센서 통신 상태. 한 번이라도 수신하면 CONNECTED 로 바뀌고 자동으로 되돌아가지 않으므로, "
                + "실시간 여부는 lastCommunicationAt 으로 판단할 것")
        private SensorStatus sensorStatus;

        @Schema(description = "마지막 센서 수신 시각. 수신 이력이 없으면 null", nullable = true)
        private LocalDateTime lastCommunicationAt;
    }

    @Getter
    @Builder
    public static class SensorBlock {
        private LocalDateTime recordedAt;

        // ── 움직임 / 추락
        private WorkerState fallState;
        private Double fallConfidence;

        @Schema(allowableValues = {"STABLE", "STUMBLE", "COLLAPSE"}, nullable = true)
        private String posture;
        private Boolean postureAbnormal;

        // ── 건강
        private WorkerState healthState;

        @Schema(description = "종합 위험 상태. fallState·healthState 중 더 심각한 값 (현재 상태 카드 색상용)")
        private WorkerState overallRiskState;

        private Double heartRate;
        private Double spo2;

        @Schema(nullable = true)
        private Double hrv;

        @Schema(description = "개인 HRV 기준선 대비 이탈 여부 (피로도)")
        private Boolean fatigueAbnormal;

        @Schema(description = "몸 신호 + 외부 기상을 조합한 최종 온열질환 위험도. NORMAL / RECOMMEND / ACTION_REQUIRED")
        private WorkerState heatRiskLevel;

        @Schema(description = "외부 기상(체감온도) 기반 위험 여부")
        private Boolean externalHeatWarning;
    }

    /**
     * @param helmet  null 이면 미배정
     * @param status  HelmetStatusResponse.of(helmet, latest) — 측정값이 없으면(recordedAt == null) sensor 를 비운다
     * @param heat    HeatRiskResponse.of(latest, ...) — 측정값이 없으면 null
     */
    public static MyHomeResponse of(Worker worker, Helmet helmet, HelmetStatusResponse status, HeatRiskResponse heat) {
        return MyHomeResponse.builder()
                .worker(WorkerBlock.builder()
                        .workerId(worker.getId())
                        .employeeNo(worker.getEmployeeNo())
                        .name(worker.getName())
                        .status(worker.getStatus())
                        .zone(toZone(worker.getCurrentZone()))
                        .build())
                .helmet(helmet == null ? null : HelmetBlock.builder()
                        .helmetId(helmet.getId())
                        .helmetNo(helmet.getHelmetNo())
                        .deviceId(helmet.getDeviceId())
                        .sensorStatus(helmet.getSensorStatus())
                        .lastCommunicationAt(helmet.getLastCommunicationAt())
                        .build())
                .sensor(status == null || heat == null ? null : SensorBlock.builder()
                        .recordedAt(status.getRecordedAt())
                        .fallState(status.getFallState())
                        .fallConfidence(status.getFallConfidence())
                        .posture(status.getPosture())
                        .postureAbnormal(status.getPostureAbnormal())
                        .healthState(status.getHealthState())
                        .overallRiskState(status.getOverallRiskState())
                        .heartRate(status.getHeartRate())
                        .spo2(status.getSpo2())
                        .hrv(status.getHrv())
                        .fatigueAbnormal(status.getFatigueAbnormal())
                        .heatRiskLevel(heat.getHeatRiskLevel())
                        .externalHeatWarning(heat.getExternalHeatWarning())
                        .build())
                .build();
    }

    private static ZoneBlock toZone(Zone zone) {
        if (zone == null) {
            return null;
        }
        return ZoneBlock.builder()
                .zoneId(zone.getId())
                .zoneCode(zone.getZoneCode())
                .zoneName(zone.getZoneName())
                .floor(zone.getFloor())
                .build();
    }
}
