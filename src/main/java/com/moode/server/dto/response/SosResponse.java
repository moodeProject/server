package com.moode.server.dto.response;

import com.moode.server.domain.Sos;
import com.moode.server.domain.SosStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SosResponse {

    private Long sosId;

    @Schema(description = "누른 작업자 ID. 작업자가 삭제됐으면 null", nullable = true)
    private Long requesterId;

    @Schema(description = "누른 순간의 이름")
    private String requesterName;

    @Schema(description = "누른 순간의 사번")
    private String requesterEmployeeNo;

    @Schema(description = "신고자가 착용 중이던 헬멧. GET /api/helmets/{helmetId}/status 바로가기용. 미착용이면 null",
            nullable = true)
    private Long helmetId;

    @Schema(description = "신고 시점 구역 코드. 위치를 못 잡았으면 null", nullable = true)
    private String zoneCode;

    @Schema(nullable = true)
    private String zoneName;

    @Schema(description = "그 구역이 기록된 시각. 오래됐으면 위치를 믿기 어렵다", nullable = true)
    private LocalDateTime zoneUpdatedAt;

    @Schema(description = "REQUESTED(요청됨) / RESOLVED(처리 완료)")
    private SosStatus status;

    private LocalDateTime createdAt;

    @Schema(nullable = true)
    private LocalDateTime resolvedAt;

    public static SosResponse from(Sos sos) {
        return SosResponse.builder()
                .sosId(sos.getId())
                .requesterId(sos.getRequester() == null ? null : sos.getRequester().getId())
                .requesterName(sos.getRequesterName())
                .requesterEmployeeNo(sos.getRequesterEmployeeNo())
                .helmetId(sos.getHelmet() == null ? null : sos.getHelmet().getId())
                .zoneCode(sos.getZone() == null ? null : sos.getZone().getZoneCode())
                .zoneName(sos.getZone() == null ? null : sos.getZone().getZoneName())
                .zoneUpdatedAt(sos.getZoneUpdatedAt())
                .status(sos.getStatus())
                .createdAt(sos.getCreatedAt())
                .resolvedAt(sos.getResolvedAt())
                .build();
    }
}
