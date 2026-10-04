package com.moode.server.dto.response;

import com.moode.server.common.Times;
import com.moode.server.domain.Helmet;
import com.moode.server.domain.OperationalStatus;
import com.moode.server.domain.SensorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 헬멧 관리 목록/등록/수정/배정 응답. */
@Getter
@Builder
public class HelmetResponse {

    private Long id;
    private String helmetNo;
    private String deviceId;

    @Schema(nullable = true)
    private Long workerId;

    @Schema(description = "미배정이면 null", nullable = true)
    private String workerName;

    private SensorStatus sensorStatus;
    private OperationalStatus operationalStatus;

    @Schema(nullable = true)
    private OffsetDateTime lastCommunicationAt;

    @Schema(nullable = true)
    private String memo;

    public static HelmetResponse from(Helmet helmet) {
        var worker = helmet.getWorker();
        return HelmetResponse.builder()
                .id(helmet.getId())
                .helmetNo(helmet.getHelmetNo())
                .deviceId(helmet.getDeviceId())
                .workerId(worker == null ? null : worker.getId())
                .workerName(worker == null ? null : worker.getName())
                .sensorStatus(helmet.getSensorStatus())
                .operationalStatus(helmet.getOperationalStatus())
                .lastCommunicationAt(Times.toOffset(helmet.getLastCommunicationAt()))
                .memo(helmet.getMemo())
                .build();
    }
}
