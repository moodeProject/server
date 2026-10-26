package com.moode.server.dto.response;

import com.moode.server.domain.HazardType;
import com.moode.server.domain.WorkerState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SensorDataResponse {
    private Long id;
    private String deviceId;

    // 추락 / 건강, 서로 독립적인 판단 결과
    @Schema(description = "추락 판정 상태. NORMAL < ACTION_REQUIRED(휘청거림) < FALLING(자세 붕괴) < FALLEN(낙상) < UNCONSCIOUS(낙상 후 의식불명)", example = "NORMAL", nullable = true)
    private WorkerState fallState;
    private WorkerState healthState;

    private Double fallConfidence;

    // 둘 중 하나라도 경고 등급 이상이면 true
    private boolean alertTriggered;

    // 헬멧이 보낸 구역 추정값 원본 (zone 테이블과 아직 연결 안 됨)
    private String zoneId;

    private LocalDateTime recordedAt;

    // 영상(헬멧 카메라)에서 최근 10초 안에 확정된 위험. 헬멧은 hazardWarningSeq 가 바뀔 때만
    // 스피커로 경고한다. 영상 단독 감지는 대시보드에 올리지 않고 이 경고로만 끝난다.
    @Schema(description = "최근 영상 위험 종류 (forklift / puddle / pothole). 없으면 null", example = "forklift", nullable = true)
    private HazardType hazardWarning;

    @Schema(description = "영상 경고 번호. 값이 바뀌었을 때만 새 경고로 취급", example = "12", nullable = true)
    private Long hazardWarningSeq;
}
