package com.moode.server.dto.response;

import com.moode.server.domain.Helmet;
import com.moode.server.domain.SensorData;
import com.moode.server.domain.WorkerState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 헬멧 1개의 최신 상태. 기존 WorkerStatusResponse 필드를 그대로 두고
 * 헬멧/작업자 식별자와 종합 위험 상태(overallRiskState)를 더했다.
 */
@Getter
@Builder
public class HelmetStatusResponse {

    // ── 헬멧·작업자 식별 (프론트가 작업자 API 와 합칠 때 쓰는 키)
    private Long helmetId;
    private String helmetNo;

    @Schema(description = "배정된 작업자 ID. 미배정이면 null", nullable = true)
    private Long workerId;

    private String deviceId;

    // ── 추락 / 건강, 서로 독립적인 판단 결과
    private WorkerState fallState;
    private WorkerState healthState;

    @Schema(description = "종합 위험 상태. fallState·healthState 중 더 심각한 값 (화면 표시용)", nullable = true)
    private WorkerState overallRiskState;

    @Schema(description = "마지막 측정 시간. 측정값이 없으면 null", nullable = true)
    private LocalDateTime recordedAt;

    private Double ax;
    private Double ay;
    private Double az;
    private Double gx;
    private Double gy;
    private Double gz;

    private Double heartRate;
    private Double spo2;

    @Schema(description = "HRV(RMSSD, ms). 계산 불가/미수신 시 null", nullable = true)
    private Double hrv;

    @Schema(description = "개인 HRV 기준선 대비 이탈 여부 (피로도 판단용)")
    private Boolean fatigueAbnormal;

    @Schema(description = "활동량 대비 심박 지속 상승 여부. 외부 기상 정보 결합 전 1차 생체신호 지표")
    private Boolean heatRiskAbnormal;

    private Double fallConfidence;

    @Schema(description = "자세 상태 원본 (센서 전송값)",
            allowableValues = {"STABLE", "STUMBLE", "COLLAPSE"}, nullable = true)
    private String posture;

    @Schema(description = "자세 이상 여부. STUMBLE·COLLAPSE 이면 true")
    private Boolean postureAbnormal;

    public static HelmetStatusResponse of(Helmet helmet, SensorData latest) {
        HelmetStatusResponseBuilder builder = HelmetStatusResponse.builder()
                .helmetId(helmet.getId())
                .helmetNo(helmet.getHelmetNo())
                .deviceId(helmet.getDeviceId())
                .workerId(helmet.getWorker() == null ? null : helmet.getWorker().getId());

        if (latest == null) {
            // 등록만 되고 아직 데이터가 안 온 헬멧 — 측정값은 전부 null
            return builder.build();
        }

        return builder
                .fallState(latest.getFallState())
                .healthState(latest.getHealthState())
                .overallRiskState(worse(latest.getFallState(), latest.getHealthState()))
                .recordedAt(latest.getRecordedAt())
                .ax(latest.getAx()).ay(latest.getAy()).az(latest.getAz())
                .gx(latest.getGx()).gy(latest.getGy()).gz(latest.getGz())
                .heartRate(latest.getHeartRate())
                .spo2(latest.getSpo2())
                .hrv(latest.getHrv())
                .fatigueAbnormal(latest.getFatigueAbnormal())
                .heatRiskAbnormal(latest.getHeatRiskAbnormal())
                .fallConfidence(latest.getFallConfidence())
                .posture(latest.getPosture())
                .postureAbnormal(isPostureAbnormal(latest.getPosture()))
                .build();
    }

    // 둘 중 더 심각한 상태. 판정은 각자 독립, 화면 표시용으로만 합친다. null 은 NORMAL 취급.
    private static WorkerState worse(WorkerState a, WorkerState b) {
        WorkerState x = (a == null) ? WorkerState.NORMAL : a;
        WorkerState y = (b == null) ? WorkerState.NORMAL : b;
        return x.ordinal() >= y.ordinal() ? x : y;
    }

    private static boolean isPostureAbnormal(String posture) {
        return "STUMBLE".equalsIgnoreCase(posture) || "COLLAPSE".equalsIgnoreCase(posture);
    }
}
