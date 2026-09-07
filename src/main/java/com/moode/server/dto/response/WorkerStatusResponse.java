package com.moode.server.dto.response;

import com.moode.server.domain.WorkerState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkerStatusResponse {
    private String deviceId;

    // 추락 / 건강, 서로 독립적인 판단 결과
    private WorkerState fallState;
    private WorkerState healthState;

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

    // 자세 이상 (근로자 모바일 움직임 상태 / 관리자 추락 상세의 "자세 변화 감지됨" 용)
    @Schema(description = "자세 상태 원본 (센서 전송값). 값이 없으면 미수신",
            example = "STUMBLE",
            allowableValues = {"STABLE", "STUMBLE", "COLLAPSE"},
            nullable = true)
    private String posture;

    @Schema(description = "자세 이상 여부. STUMBLE·COLLAPSE 이면 true. "
            + "관리자 추락 상세의 '자세 변화 감지됨' / 근로자 모바일 움직임 상태 표시에 사용",
            example = "true")
    private Boolean postureAbnormal;
}
